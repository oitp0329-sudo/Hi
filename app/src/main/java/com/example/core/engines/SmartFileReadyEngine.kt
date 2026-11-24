package com.example.core.engines

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.core.design.formatFileSize
import com.example.core.model.ImageOutputFormat
import com.example.core.model.SmartPreset
import com.example.core.model.SmartReadyResult
import com.example.core.model.SmartTargetFormat
import com.example.core.model.ValidationCheckItem
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class SmartFileReadyEngine(
    private val pdfEngine: PdfProcessingEngine
) {
    suspend fun processAndValidate(
        input: WorkspaceFile,
        preset: SmartPreset,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ): Result<SmartReadyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(input.localPath)
            require(sourceFile.exists()) { "Source file is missing from workspace." }

            val isInputPdf = input.mimeType == "application/pdf" || input.displayName.lowercase().endsWith(".pdf")
            val isInputImage = input.mimeType.startsWith("image/") ||
                input.displayName.lowercase().let {
                    it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".bmp")
                }

            require(isInputPdf || isInputImage) {
                "Smart File Ready supports image and PDF files. Selected file type (${input.mimeType}) is not supported."
            }

            onProgress(15)

            when (preset.targetFormat) {
                SmartTargetFormat.PDF -> {
                    if (isInputImage) {
                        val tempPdf = File(outputFile.parentFile, "tmp_img2pdf_${System.currentTimeMillis()}.pdf")
                        pdfEngine.imagesToPdf(listOf(sourceFile), tempPdf, preset.pdfPageSize) { p ->
                            onProgress(15 + (p * 35) / 100)
                        }.getOrThrow()
                        if (tempPdf.length() > preset.maxSizeBytes) {
                            pdfEngine.compressPdf(tempPdf, outputFile, preset.qualityPercent.coerceAtMost(75), 1.0f) { p ->
                                onProgress(50 + (p * 40) / 100)
                            }.getOrThrow()
                            tempPdf.delete()
                        } else {
                            tempPdf.copyTo(outputFile, overwrite = true)
                            tempPdf.delete()
                        }
                    } else {
                        // Input is PDF -> resize pages if needed, then compress if above maxSizeBytes
                        var currentPdf = sourceFile
                        val resizedTmp = File(outputFile.parentFile, "tmp_resized_${System.currentTimeMillis()}.pdf")
                        if (preset.pdfPageSize.widthPt > 0) {
                            pdfEngine.changePageSizeAndOrientation(sourceFile, resizedTmp, preset.pdfPageSize, landscape = false).getOrThrow()
                            currentPdf = resizedTmp
                        }
                        if (currentPdf.length() > preset.maxSizeBytes) {
                            // Try up to two compression passes to meet byte cap
                            pdfEngine.compressPdf(currentPdf, outputFile, preset.qualityPercent.coerceAtMost(78), 1.1f) { p ->
                                onProgress(35 + (p * 35) / 100)
                            }.getOrThrow()
                            if (outputFile.length() > preset.maxSizeBytes) {
                                val secondPassTmp = File(outputFile.parentFile, "tmp_pass2_${System.currentTimeMillis()}.pdf")
                                outputFile.copyTo(secondPassTmp, overwrite = true)
                                pdfEngine.compressPdf(secondPassTmp, outputFile, 45, 0.85f) { p ->
                                    onProgress(70 + (p * 20) / 100)
                                }.getOrThrow()
                                secondPassTmp.delete()
                            }
                        } else {
                            currentPdf.copyTo(outputFile, overwrite = true)
                        }
                        if (resizedTmp.exists()) resizedTmp.delete()
                    }
                }

                SmartTargetFormat.JPG, SmartTargetFormat.PNG, SmartTargetFormat.WEBP -> {
                    val workingImgFile: File
                    var tempRenderedImg: File? = null
                    if (isInputPdf) {
                        val bmp = pdfEngine.renderPageBitmap(sourceFile, 0, targetWidthPx = 1400).getOrThrow()
                        val tmp = File(outputFile.parentFile, "tmp_pdf_p1_${System.currentTimeMillis()}.png")
                        ImageProcessingEngine.writeBitmapToFile(bmp, tmp, ImageOutputFormat.PNG, 100)
                        bmp.recycle()
                        workingImgFile = tmp
                        tempRenderedImg = tmp
                    } else {
                        workingImgFile = sourceFile
                    }

                    try {
                        val outFormat = when (preset.targetFormat) {
                            SmartTargetFormat.JPG -> ImageOutputFormat.JPG
                            SmartTargetFormat.PNG -> ImageOutputFormat.PNG
                            SmartTargetFormat.WEBP -> ImageOutputFormat.WEBP
                            SmartTargetFormat.PDF -> ImageOutputFormat.JPG
                        }

                        // Compute target dimensions if preset requires bounds
                        val info = ImageProcessingEngine.inspectImage(workingImgFile, "image/*").getOrThrow()
                        val (targetW, targetH) = computeTargetImageDimensions(
                            srcW = info.widthPx,
                            srcH = info.heightPx,
                            preset = preset
                        )

                        val config = ImageEditConfig(
                            outputFormat = outFormat,
                            qualityPercent = preset.qualityPercent,
                            targetMaxBytes = preset.maxSizeBytes,
                            targetWidthPx = targetW,
                            targetHeightPx = targetH
                        )
                        ImageProcessingEngine.processAndSave(workingImgFile, outputFile, config) { p ->
                            onProgress(20 + (p * 70) / 100)
                        }.getOrThrow()
                    } finally {
                        tempRenderedImg?.delete()
                    }
                }
            }

            onProgress(95)
            // Strict post-export independent verification on the actual file written to disk!
            val result = verifyOutputFile(
                originalSizeBytes = input.sizeBytes,
                outputFile = outputFile,
                preset = preset
            )
            onProgress(100)
            result
        }
    }

    private fun computeTargetImageDimensions(
        srcW: Int,
        srcH: Int,
        preset: SmartPreset
    ): Pair<Int?, Int?> {
        if (srcW <= 0 || srcH <= 0) return null to null
        var w = srcW
        var h = srcH

        // Enforce aspect ratio if specified (e.g. "35:45")
        val ratioParts = preset.aspectRatioLabel.split(":").mapNotNull { it.trim().toFloatOrNull() }
        if (ratioParts.size == 2 && ratioParts[0] > 0f && ratioParts[1] > 0f) {
            val targetRatio = ratioParts[0] / ratioParts[1]
            h = (w / targetRatio).toInt().coerceAtLeast(100)
        }

        if (preset.maxWidthPx > 0 && w > preset.maxWidthPx) {
            val scale = preset.maxWidthPx.toFloat() / w
            w = preset.maxWidthPx
            h = (h * scale).toInt().coerceAtLeast(50)
        }
        if (preset.maxHeightPx > 0 && h > preset.maxHeightPx) {
            val scale = preset.maxHeightPx.toFloat() / h
            h = preset.maxHeightPx
            w = (w * scale).toInt().coerceAtLeast(50)
        }
        if (preset.minWidthPx > 0 && w < preset.minWidthPx) {
            val scale = preset.minWidthPx.toFloat() / w
            w = preset.minWidthPx
            h = (h * scale).toInt().coerceAtLeast(50)
        }
        if (preset.minHeightPx > 0 && h < preset.minHeightPx) {
            val scale = preset.minHeightPx.toFloat() / h
            h = preset.minHeightPx
            w = (w * scale).toInt().coerceAtLeast(50)
        }
        return if (w == srcW && h == srcH) null to null else w to h
    }

    private fun verifyOutputFile(
        originalSizeBytes: Long,
        outputFile: File,
        preset: SmartPreset
    ): SmartReadyResult {
        require(outputFile.exists() && outputFile.length() > 0L) {
            "Output file verification failed: file was not written."
        }

        val actualSize = outputFile.length()
        val checks = mutableListOf<ValidationCheckItem>()

        // 1. Max size check
        val sizePassed = actualSize <= preset.maxSizeBytes
        checks.add(
            ValidationCheckItem(
                ruleName = "Maximum File Size",
                expected = "≤ ${formatFileSize(preset.maxSizeBytes)}",
                actual = formatFileSize(actualSize),
                passed = sizePassed
            )
        )

        // 2. Actual file signature / format check
        val header = outputFile.inputStream().use { ins ->
            ByteArray(12).also { ins.read(it) }
        }
        val detectedFormat = when {
            header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() -> SmartTargetFormat.PDF
            header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> SmartTargetFormat.JPG
            header.size >= 4 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() -> SmartTargetFormat.PNG
            header.size >= 12 && String(header.copyOfRange(0, 4)) == "RIFF" && String(header.copyOfRange(8, 12)) == "WEBP" -> SmartTargetFormat.WEBP
            else -> null
        }
        val formatPassed = detectedFormat == preset.targetFormat
        checks.add(
            ValidationCheckItem(
                ruleName = "Output Format",
                expected = preset.targetFormat.label,
                actual = detectedFormat?.label ?: "Unverified",
                passed = formatPassed
            )
        )

        var outW = 0
        var outH = 0
        var outPages = 0

        if (preset.targetFormat == SmartTargetFormat.PDF) {
            runCatching {
                ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        outPages = renderer.pageCount
                        if (outPages > 0) {
                            renderer.openPage(0).use { p ->
                                outW = p.width
                                outH = p.height
                            }
                        }
                    }
                }
            }
            if (outPages == 0) {
                runCatching {
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(outputFile).use { doc ->
                        outPages = doc.numberOfPages
                        if (outPages > 0) {
                            val box = doc.getPage(0).mediaBox
                            outW = box.width.toInt()
                            outH = box.height.toInt()
                        }
                    }
                }
            }
            checks.add(
                ValidationCheckItem(
                    ruleName = "PDF Readability",
                    expected = "≥ 1 valid page",
                    actual = if (outPages > 0) "$outPages page(s) (${outW}×${outH} pt)" else "Unreadable",
                    passed = outPages > 0
                )
            )
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outputFile.absolutePath, opts)
            outW = opts.outWidth.coerceAtLeast(0)
            outH = opts.outHeight.coerceAtLeast(0)

            if (preset.minWidthPx > 0 || preset.maxWidthPx > 0) {
                val minOk = preset.minWidthPx == 0 || outW >= preset.minWidthPx
                val maxOk = preset.maxWidthPx == 0 || outW <= preset.maxWidthPx
                checks.add(
                    ValidationCheckItem(
                        ruleName = "Width Bounds",
                        expected = "${preset.minWidthPx}–${if (preset.maxWidthPx > 0) preset.maxWidthPx else "∞"} px",
                        actual = "$outW px",
                        passed = minOk && maxOk
                    )
                )
            }
            if (preset.minHeightPx > 0 || preset.maxHeightPx > 0) {
                val minOk = preset.minHeightPx == 0 || outH >= preset.minHeightPx
                val maxOk = preset.maxHeightPx == 0 || outH <= preset.maxHeightPx
                checks.add(
                    ValidationCheckItem(
                        ruleName = "Height Bounds",
                        expected = "${preset.minHeightPx}–${if (preset.maxHeightPx > 0) preset.maxHeightPx else "∞"} px",
                        actual = "$outH px",
                        passed = minOk && maxOk
                    )
                )
            }
            val ratioParts = preset.aspectRatioLabel.split(":").mapNotNull { it.trim().toFloatOrNull() }
            if (ratioParts.size == 2 && ratioParts[0] > 0f && ratioParts[1] > 0f && outH > 0) {
                val expectedRatio = ratioParts[0] / ratioParts[1]
                val actualRatio = outW.toFloat() / outH.toFloat()
                val ratioOk = abs(expectedRatio - actualRatio) <= 0.05f
                checks.add(
                    ValidationCheckItem(
                        ruleName = "Aspect Ratio",
                        expected = preset.aspectRatioLabel,
                        actual = String.format("%.2f:1", actualRatio),
                        passed = ratioOk
                    )
                )
            }
        }

        return SmartReadyResult(
            outputPath = outputFile.absolutePath,
            outputName = outputFile.name,
            outputMimeType = preset.targetFormat.mimeType,
            originalSizeBytes = originalSizeBytes,
            outputSizeBytes = actualSize,
            outputWidthPx = outW,
            outputHeightPx = outH,
            outputPageCount = outPages,
            checks = checks,
            allPassed = checks.all { it.passed }
        )
    }
}
