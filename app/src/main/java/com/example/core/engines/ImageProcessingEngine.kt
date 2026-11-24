package com.example.core.engines

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.core.model.ImageOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ImageFilterPreset(val label: String) {
    NONE("Original"),
    DOCUMENT_ENHANCE("Doc Enhance"),
    GRAYSCALE("Mono Ink"),
    WARM("Warm"),
    COOL("Cool"),
    HIGH_CONTRAST("Crisp")
}

data class ImageEditConfig(
    val outputFormat: ImageOutputFormat = ImageOutputFormat.JPG,
    val qualityPercent: Int = 90,
    val targetMaxBytes: Long? = null,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeftFrac: Float = 0f,
    val cropTopFrac: Float = 0f,
    val cropRightFrac: Float = 1f,
    val cropBottomFrac: Float = 1f,
    val targetWidthPx: Int? = null,
    val targetHeightPx: Int? = null,
    val resizePercent: Int? = null,
    val brightness: Float = 0f,      // -100..100
    val contrast: Float = 1f,        // 0.5..2.0
    val saturation: Float = 1f,      // 0..2.0
    val exposure: Float = 0f,        // -1.0..1.0
    val sharpness: Float = 0f,       // 0..1.0
    val blurRadius: Int = 0,         // 0..12
    val filterPreset: ImageFilterPreset = ImageFilterPreset.NONE,
    val removeSimpleBackground: Boolean = false,
    val backgroundColor: Int? = null,
    val overlayText: String = "",
    val watermarkText: String = "",
    val stickerLabel: String = "",
    val addShapeBadge: Boolean = false,
    val borderWidthPx: Int = 0,
    val borderColor: Int = Color.WHITE
)

data class ImageInfoResult(
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val exifSummary: Map<String, String>
)

object ImageProcessingEngine {

    suspend fun inspectImage(file: File, mimeType: String): Result<ImageInfoResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(file.exists()) { "Image file not found." }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val exifMap = linkedMapOf<String, String>()
                runCatching {
                    val exif = ExifInterface(file.absolutePath)
                    val tags = listOf(
                        ExifInterface.TAG_MAKE to "Camera Make",
                        ExifInterface.TAG_MODEL to "Camera Model",
                        ExifInterface.TAG_DATETIME_ORIGINAL to "Date Taken",
                        ExifInterface.TAG_IMAGE_WIDTH to "EXIF Width",
                        ExifInterface.TAG_IMAGE_LENGTH to "EXIF Height",
                        ExifInterface.TAG_ORIENTATION to "Orientation",
                        ExifInterface.TAG_ISO_SPEED_RATINGS to "ISO",
                        ExifInterface.TAG_F_NUMBER to "Aperture",
                        ExifInterface.TAG_EXPOSURE_TIME to "Shutter",
                        ExifInterface.TAG_GPS_LATITUDE to "GPS Latitude",
                        ExifInterface.TAG_GPS_LONGITUDE to "GPS Longitude"
                    )
                    for ((tag, label) in tags) {
                        val value = exif.getAttribute(tag)
                        if (!value.isNullOrBlank()) {
                            exifMap[label] = value
                        }
                    }
                }
                ImageInfoResult(
                    widthPx = opts.outWidth.coerceAtLeast(0),
                    heightPx = opts.outHeight.coerceAtLeast(0),
                    sizeBytes = file.length(),
                    mimeType = mimeType,
                    exifSummary = exifMap
                )
            }
        }

    suspend fun stripExifMetadata(
        input: File,
        output: File,
        format: ImageOutputFormat = ImageOutputFormat.JPG,
        quality: Int = 92
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decodeSampledBitmap(input, maxDim = 4096)
            try {
                writeBitmapToFile(bmp, output, format, quality)
            } finally {
                bmp.recycle()
            }
            output
        }
    }

    suspend fun renderPreviewBitmap(input: File, config: ImageEditConfig, maxPreviewDim: Int = 960): Result<Bitmap> =
        withContext(Dispatchers.Default) {
            runCatching {
                val base = decodeSampledBitmap(input, maxPreviewDim)
                try {
                    applyEditsToBitmap(base, config, isPreview = true)
                } finally {
                    base.recycle()
                }
            }
        }

    suspend fun processAndSave(
        input: File,
        output: File,
        config: ImageEditConfig,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(15)
            val source = decodeSampledBitmap(input, maxDim = 4096)
            val edited = try {
                onProgress(45)
                applyEditsToBitmap(source, config, isPreview = false)
            } finally {
                source.recycle()
            }

            try {
                onProgress(75)
                val targetBytes = config.targetMaxBytes
                if (targetBytes != null && targetBytes > 0L && config.outputFormat != ImageOutputFormat.PNG && config.outputFormat != ImageOutputFormat.BMP) {
                    compressToTargetBytes(edited, output, config.outputFormat, config.qualityPercent, targetBytes)
                } else {
                    writeBitmapToFile(edited, output, config.outputFormat, config.qualityPercent)
                }
                onProgress(100)
                output
            } finally {
                edited.recycle()
            }
        }
    }

    suspend fun createCollage(
        imageFiles: List<File>,
        output: File,
        format: ImageOutputFormat = ImageOutputFormat.JPG,
        spacingPx: Int = 20,
        backgroundColor: Int = Color.WHITE
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageFiles.size in 2..4) { "Select 2 to 4 images for a collage." }
            val canvasSize = 1400
            val cols = 2
            val rows = if (imageFiles.size <= 2) 1 else 2
            val cellW = (canvasSize - spacingPx * (cols + 1)) / cols
            val cellH = (canvasSize - spacingPx * (rows + 1)) / rows
            val resultH = cellH * rows + spacingPx * (rows + 1)

            val outBmp = Bitmap.createBitmap(canvasSize, resultH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBmp)
            canvas.drawColor(backgroundColor)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            try {
                imageFiles.forEachIndexed { idx, file ->
                    val col = idx % cols
                    val row = idx / cols
                    val left = (spacingPx + col * (cellW + spacingPx)).toFloat()
                    val top = (spacingPx + row * (cellH + spacingPx)).toFloat()
                    val bmp = decodeSampledBitmap(file, maxDim = 1000)
                    try {
                        val scale = maxOf(cellW.toFloat() / bmp.width, cellH.toFloat() / bmp.height)
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val dx = left + (cellW - drawW) / 2f
                        val dy = top + (cellH - drawH) / 2f
                        canvas.save()
                        canvas.clipRect(left, top, left + cellW, top + cellH)
                        canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + drawW, dy + drawH), paint)
                        canvas.restore()
                    } finally {
                        bmp.recycle()
                    }
                }
                writeBitmapToFile(outBmp, output, format, 92)
            } finally {
                outBmp.recycle()
            }
            output
        }
    }

    fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val longest = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (longest / sample > maxDim) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalStateException("Unable to decode image ${file.name}")

        // Respect EXIF orientation
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        return if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }

    private fun applyEditsToBitmap(source: Bitmap, config: ImageEditConfig, isPreview: Boolean): Bitmap {
        // 1. Crop
        val l = (config.cropLeftFrac.coerceIn(0f, 0.9f) * source.width).roundToInt()
        val t = (config.cropTopFrac.coerceIn(0f, 0.9f) * source.height).roundToInt()
        val r = (config.cropRightFrac.coerceIn(0.1f, 1f) * source.width).roundToInt().coerceAtLeast(l + 10)
        val b = (config.cropBottomFrac.coerceIn(0.1f, 1f) * source.height).roundToInt().coerceAtLeast(t + 10)
        var working = if (l > 0 || t > 0 || r < source.width || b < source.height) {
            Bitmap.createBitmap(source, l, t, (r - l).coerceAtMost(source.width - l), (b - t).coerceAtMost(source.height - t))
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 2. Rotate / Flip
        if (config.rotationDegrees != 0f || config.flipHorizontal || config.flipVertical) {
            val matrix = Matrix().apply {
                if (config.rotationDegrees != 0f) postRotate(config.rotationDegrees)
                val sx = if (config.flipHorizontal) -1f else 1f
                val sy = if (config.flipVertical) -1f else 1f
                if (sx != 1f || sy != 1f) postScale(sx, sy)
            }
            val transformed = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
            if (transformed != working) working.recycle()
            working = transformed
        }

        // 3. Resize (skip exact large scaling during preview to keep UI instant)
        if (!isPreview) {
            val targetW: Int
            val targetH: Int
            when {
                config.targetWidthPx != null && config.targetWidthPx > 0 && config.targetHeightPx != null && config.targetHeightPx > 0 -> {
                    targetW = config.targetWidthPx.coerceIn(16, 6000)
                    targetH = config.targetHeightPx.coerceIn(16, 6000)
                }
                config.targetWidthPx != null && config.targetWidthPx > 0 -> {
                    targetW = config.targetWidthPx.coerceIn(16, 6000)
                    targetH = (working.height * (targetW.toFloat() / working.width)).roundToInt().coerceAtLeast(16)
                }
                config.resizePercent != null && config.resizePercent in 10..200 && config.resizePercent != 100 -> {
                    val scale = config.resizePercent / 100f
                    targetW = (working.width * scale).roundToInt().coerceAtLeast(16)
                    targetH = (working.height * scale).roundToInt().coerceAtLeast(16)
                }
                else -> {
                    targetW = working.width
                    targetH = working.height
                }
            }
            if (targetW != working.width || targetH != working.height) {
                val scaled = Bitmap.createScaledBitmap(working, targetW, targetH, true)
                if (scaled != working) working.recycle()
                working = scaled
            }
        }

        // 4. ColorMatrix adjustments & filters
        val cm = buildColorMatrix(config)
        val filtered = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filtered)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(working, 0f, 0f, paint)
        working.recycle()
        working = filtered

        // 5. Simple background removal / replacement (uniform backdrop matte sampled from corners)
        if (config.removeSimpleBackground || config.backgroundColor != null) {
            val matted = applyUniformBackgroundMatte(working, config.backgroundColor)
            working.recycle()
            working = matted
        }

        // 6. Blur or Sharpness
        if (config.blurRadius > 0) {
            val blurred = fastBoxBlur(working, config.blurRadius.coerceIn(1, 10))
            working.recycle()
            working = blurred
        } else if (config.sharpness > 0.05f) {
            val sharpened = applySharpness(working, config.sharpness.coerceIn(0f, 1f))
            working.recycle()
            working = sharpened
        }

        // 7. Overlays (shapes, text, sticker, watermark, border)
        working = drawOverlaysAndBorder(working, config)
        return working
    }

    private fun buildColorMatrix(config: ImageEditConfig): ColorMatrix {
        val result = ColorMatrix()

        // Filter preset base
        when (config.filterPreset) {
            ImageFilterPreset.GRAYSCALE -> result.setSaturation(0f)
            ImageFilterPreset.DOCUMENT_ENHANCE -> {
                val docCm = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -18f,
                        0f, 1.25f, 0f, 0f, -18f,
                        0f, 0f, 1.25f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                result.postConcat(docCm)
            }
            ImageFilterPreset.WARM -> {
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.08f, 0f, 0f, 0f, 8f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 0.92f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ImageFilterPreset.COOL -> {
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.93f, 0f, 0f, 0f, 0f,
                            0f, 1.0f, 0f, 0f, 4f,
                            0f, 0f, 1.1f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ImageFilterPreset.HIGH_CONTRAST -> {
                val c = 1.3f
                val t = (-0.5f * c + 0.5f) * 255f
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ImageFilterPreset.NONE -> Unit
        }

        // Saturation
        if (config.saturation != 1f && config.filterPreset != ImageFilterPreset.GRAYSCALE) {
            val sat = ColorMatrix().apply { setSaturation(config.saturation.coerceIn(0f, 2f)) }
            result.postConcat(sat)
        }

        // Contrast, Brightness, Exposure
        val c = config.contrast.coerceIn(0.4f, 2.2f)
        val expFactor = (1f + config.exposure.coerceIn(-0.6f, 0.6f))
        val scale = c * expFactor
        val brightOffset = config.brightness.coerceIn(-100f, 100f) * 1.6f
        val translate = (-0.5f * c + 0.5f) * 255f + brightOffset
        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return result
    }

    private fun applyUniformBackgroundMatte(src: Bitmap, replacementColor: Int?): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val corners = listOf(
            pixels[0],
            pixels[w - 1],
            pixels[(h - 1) * w],
            pixels[h * w - 1]
        )
        val avgR = corners.map { Color.red(it) }.average().roundToInt()
        val avgG = corners.map { Color.green(it) }.average().roundToInt()
        val avgB = corners.map { Color.blue(it) }.average().roundToInt()
        val threshold = 48

        for (i in pixels.indices) {
            val p = pixels[i]
            val dr = abs(Color.red(p) - avgR)
            val dg = abs(Color.green(p) - avgG)
            val db = abs(Color.blue(p) - avgB)
            if (dr < threshold && dg < threshold && db < threshold) {
                pixels[i] = replacementColor ?: Color.TRANSPARENT
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scaleDown = (w / 400f).coerceAtLeast(1f)
        val smallW = (w / scaleDown).roundToInt().coerceAtLeast(16)
        val smallH = (h / scaleDown).roundToInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val pix = IntArray(smallW * smallH)
        small.getPixels(pix, 0, smallW, 0, 0, smallW, smallH)
        val outPix = IntArray(pix.size)
        val r = radius.coerceIn(1, 6)
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dy in -r..r) {
                    val ny = (y + dy).coerceIn(0, smallH - 1)
                    for (dx in -r..r) {
                        val nx = (x + dx).coerceIn(0, smallW - 1)
                        val c = pix[ny * smallW + nx]
                        aSum += Color.alpha(c)
                        rSum += Color.red(c)
                        gSum += Color.green(c)
                        bSum += Color.blue(c)
                        count++
                    }
                }
                outPix[y * smallW + x] = Color.argb(aSum / count, rSum / count, gSum / count, bSum / count)
            }
        }
        small.setPixels(outPix, 0, smallW, 0, 0, smallW, smallH)
        val restored = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        return restored
    }

    private fun applySharpness(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val out = pix.copyOf()
        val factor = amount * 0.65f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pix[idx]
                val up = pix[idx - w]
                val down = pix[idx + w]
                val left = pix[idx - 1]
                val right = pix[idx + 1]
                val avgR = (Color.red(up) + Color.red(down) + Color.red(left) + Color.red(right)) / 4
                val avgG = (Color.green(up) + Color.green(down) + Color.green(left) + Color.green(right)) / 4
                val avgB = (Color.blue(up) + Color.blue(down) + Color.blue(left) + Color.blue(right)) / 4
                val nr = (Color.red(c) + (Color.red(c) - avgR) * factor).roundToInt().coerceIn(0, 255)
                val ng = (Color.green(c) + (Color.green(c) - avgG) * factor).roundToInt().coerceIn(0, 255)
                val nb = (Color.blue(c) + (Color.blue(c) - avgB) * factor).roundToInt().coerceIn(0, 255)
                out[idx] = Color.argb(Color.alpha(c), nr, ng, nb)
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun drawOverlaysAndBorder(src: Bitmap, config: ImageEditConfig): Bitmap {
        val border = config.borderWidthPx.coerceIn(0, 80)
        val finalW = src.width + border * 2
        val finalH = src.height + border * 2
        val out = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (border > 0) {
            canvas.drawColor(config.borderColor)
        }
        canvas.drawBitmap(src, border.toFloat(), border.toFloat(), null)
        src.recycle()

        if (config.addShapeBadge) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 36, 132, 93)
                style = Paint.Style.FILL
            }
            val r = (minOf(finalW, finalH) * 0.08f).coerceAtLeast(24f)
            canvas.drawCircle(finalW - border - r - 18f, border + r + 18f, r, badgePaint)
        }

        if (config.overlayText.isNotBlank()) {
            val textSize = (finalW * 0.055f).coerceIn(22f, 96f)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 32, 43, 38)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                isFakeBoldText = true
            }
            val text = config.overlayText.take(48)
            val textW = textPaint.measureText(text)
            val x = ((finalW - textW) / 2f).coerceAtLeast(24f)
            val y = finalH - border - 36f
            canvas.drawRoundRect(
                x - 18f, y - textSize - 8f, x + textW + 18f, y + 16f,
                16f, 16f, bgPaint
            )
            canvas.drawText(text, x, y, textPaint)
        }

        if (config.stickerLabel.isNotBlank()) {
            val stickerSize = (finalW * 0.05f).coerceIn(20f, 84f)
            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(227, 242, 232)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(23, 91, 67)
                textSize = stickerSize
                isFakeBoldText = true
            }
            val s = config.stickerLabel.take(24)
            val sw = labelPaint.measureText(s)
            val sx = border + 28f
            val sy = border + stickerSize + 36f
            canvas.drawRoundRect(sx - 16f, sy - stickerSize - 6f, sx + sw + 16f, sy + 14f, 20f, 20f, chipPaint)
            canvas.drawText(s, sx, sy, labelPaint)
        }

        if (config.watermarkText.isNotBlank()) {
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(95, 255, 255, 255)
                textSize = (finalW * 0.06f).coerceIn(24f, 84f)
                isFakeBoldText = true
            }
            canvas.save()
            canvas.rotate(-24f, finalW / 2f, finalH / 2f)
            val wm = config.watermarkText.take(32)
            val ww = wmPaint.measureText(wm)
            canvas.drawText(wm, (finalW - ww) / 2f, finalH / 2f, wmPaint)
            canvas.restore()
        }

        return out
    }

    private fun compressToTargetBytes(
        bitmap: Bitmap,
        output: File,
        format: ImageOutputFormat,
        initialQuality: Int,
        targetMaxBytes: Long
    ) {
        var currentBmp = bitmap
        var ownsCurrent = false
        var bestBytes: ByteArray? = null

        try {
            for (scaleStep in 0..4) {
                var low = 15
                var high = initialQuality.coerceIn(20, 100)
                while (low <= high) {
                    val mid = (low + high) / 2
                    val bytes = encodeBitmapToBytes(currentBmp, format, mid)
                    if (bytes.size <= targetMaxBytes) {
                        bestBytes = bytes
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                if (bestBytes != null) break

                // Scale down 20% and retry if quality alone couldn't reach target size
                val nextW = (currentBmp.width * 0.8f).roundToInt().coerceAtLeast(100)
                val nextH = (currentBmp.height * 0.8f).roundToInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(currentBmp, nextW, nextH, true)
                if (ownsCurrent) currentBmp.recycle()
                currentBmp = scaled
                ownsCurrent = true
            }

            val finalBytes = bestBytes ?: encodeBitmapToBytes(currentBmp, format, 20)
            FileOutputStream(output).use { it.write(finalBytes) }
        } finally {
            if (ownsCurrent) currentBmp.recycle()
        }
    }

    fun writeBitmapToFile(bitmap: Bitmap, output: File, format: ImageOutputFormat, quality: Int) {
        if (format == ImageOutputFormat.BMP) {
            writeBmpFile(bitmap, output)
            return
        }
        val bytes = encodeBitmapToBytes(bitmap, format, quality)
        FileOutputStream(output).use { it.write(bytes) }
    }

    private fun encodeBitmapToBytes(bitmap: Bitmap, format: ImageOutputFormat, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageOutputFormat.JPG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            ImageOutputFormat.BMP -> Bitmap.CompressFormat.PNG
        }
        bitmap.compress(compressFormat, quality.coerceIn(10, 100), bos)
        return bos.toByteArray()
    }

    private fun writeBmpFile(bitmap: Bitmap, output: File) {
        val w = bitmap.width
        val h = bitmap.height
        val rowBytes = w * 3
        val padding = (4 - (rowBytes % 4)) % 4
        val imageSize = (rowBytes + padding) * h
        val fileSize = 54 + imageSize
        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(fileSize)
            putInt(0)
            putInt(54)
            putInt(40)
            putInt(w)
            putInt(h)
            putShort(1)
            putShort(24)
            putInt(0)
            putInt(imageSize)
            putInt(2835)
            putInt(2835)
            putInt(0)
            putInt(0)
        }.array()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        FileOutputStream(output).use { fos ->
            fos.write(header)
            val rowBuf = ByteArray(rowBytes + padding)
            for (y in h - 1 downTo 0) {
                var idx = 0
                for (x in 0 until w) {
                    val c = pixels[y * w + x]
                    rowBuf[idx++] = Color.blue(c).toByte()
                    rowBuf[idx++] = Color.green(c).toByte()
                    rowBuf[idx++] = Color.red(c).toByte()
                }
                for (p in 0 until padding) rowBuf[idx++] = 0
                fos.write(rowBuf)
            }
        }
    }
}
