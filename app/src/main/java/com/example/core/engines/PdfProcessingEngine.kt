package com.example.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PdfDocumentDetails(
    val pageCount: Int,
    val fileSizeBytes: Long,
    val firstPageWidthPt: Float,
    val firstPageHeightPt: Float,
    val isEncrypted: Boolean,
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val formFieldNames: List<String>
)

data class PdfPageEditSpec(
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0,
    val keep: Boolean = true
)

class PdfProcessingEngine(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun inspectPdf(file: File, password: String? = null): Result<PdfDocumentDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(file.exists()) { "PDF file does not exist." }
                val doc = if (!password.isNullOrEmpty()) {
                    PDDocument.load(file, password)
                } else {
                    PDDocument.load(file)
                }
                doc.use { pd ->
                    val firstBox = if (pd.numberOfPages > 0) pd.getPage(0).mediaBox else PDRectangle.A4
                    val info = pd.documentInformation
                    val formNames = pd.documentCatalog.acroForm?.fields?.mapNotNull { it.fullyQualifiedName }
                        ?: emptyList()
                    PdfDocumentDetails(
                        pageCount = pd.numberOfPages,
                        fileSizeBytes = file.length(),
                        firstPageWidthPt = firstBox.width,
                        firstPageHeightPt = firstBox.height,
                        isEncrypted = pd.isEncrypted,
                        title = info?.title.orEmpty(),
                        author = info?.author.orEmpty(),
                        subject = info?.subject.orEmpty(),
                        keywords = info?.keywords.orEmpty(),
                        formFieldNames = formNames
                    )
                }
            }
        }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 900
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(pageIndex in 0 until renderer.pageCount) {
                        "Invalid page index $pageIndex (pages: ${renderer.pageCount})."
                    }
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val bmpW = targetWidthPx.coerceAtLeast(100)
                        val bmpH = (page.height * scale).toInt().coerceAtLeast(100)
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }
    }

    suspend fun compressPdf(
        input: File,
        output: File,
        qualityPercent: Int,
        targetDpiScale: Float = 1.25f,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount.coerceAtLeast(1)
                    val outPdf = PdfDocument()
                    try {
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val w = (page.width * targetDpiScale).toInt().coerceIn(300, 1800)
                                val h = (page.height * targetDpiScale).toInt().coerceIn(300, 2400)
                                val rawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                rawBitmap.eraseColor(Color.WHITE)
                                page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val jpegBytes = java.io.ByteArrayOutputStream().use { bos ->
                                    rawBitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(20, 95), bos)
                                    bos.toByteArray()
                                }
                                rawBitmap.recycle()

                                val compressedBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                                val outPage = outPdf.startPage(pageInfo)
                                outPage.canvas.drawBitmap(
                                    compressedBmp,
                                    null,
                                    Rect(0, 0, page.width, page.height),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                                outPdf.finishPage(outPage)
                                compressedBmp.recycle()
                            }
                            onProgress(((i + 1) * 100) / total)
                        }
                        FileOutputStream(output).use { outPdf.writeTo(it) }
                    } finally {
                        outPdf.close()
                    }
                }
            }
            output
        }
    }

    suspend fun mergePdfs(
        inputs: List<File>,
        output: File,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputs.size >= 2) { "Select at least two PDF files to merge." }
            val merger = PDFMergerUtility()
            merger.destinationFileName = output.absolutePath
            inputs.forEachIndexed { index, file ->
                merger.addSource(file)
                onProgress(((index + 1) * 50) / inputs.size)
            }
            merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
            onProgress(100)
            output
        }
    }

    suspend fun splitPdfIntoRanges(
        input: File,
        outputDir: File,
        baseName: String,
        pagesPerSplit: Int
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            require(pagesPerSplit >= 1) { "Pages per file must be at least 1." }
            val results = mutableListOf<File>()
            PDDocument.load(input).use { source ->
                val total = source.numberOfPages
                var partIndex = 1
                var start = 0
                while (start < total) {
                    val end = (start + pagesPerSplit).coerceAtMost(total)
                    val outFile = File(outputDir, "${baseName}_part_$partIndex.pdf")
                    PDDocument().use { partDoc ->
                        for (p in start until end) {
                            partDoc.importPage(source.getPage(p))
                        }
                        partDoc.save(outFile)
                    }
                    results.add(outFile)
                    partIndex++
                    start = end
                }
            }
            results
        }
    }

    suspend fun extractPages(
        input: File,
        output: File,
        oneBasedPages: List<Int>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { source ->
                val validIndices = oneBasedPages
                    .map { it - 1 }
                    .filter { it in 0 until source.numberOfPages }
                    .distinct()
                require(validIndices.isNotEmpty()) { "No valid page numbers specified." }
                PDDocument().use { outDoc ->
                    validIndices.forEach { idx ->
                        outDoc.importPage(source.getPage(idx))
                    }
                    outDoc.save(output)
                }
            }
            output
        }
    }

    suspend fun applyPageEdits(
        input: File,
        output: File,
        edits: List<PdfPageEditSpec>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val kept = edits.filter { it.keep }
            require(kept.isNotEmpty()) { "At least one page must remain in the PDF." }
            PDDocument.load(input).use { source ->
                PDDocument().use { outDoc ->
                    kept.forEach { spec ->
                        if (spec.originalPageIndex in 0 until source.numberOfPages) {
                            val imported = outDoc.importPage(source.getPage(spec.originalPageIndex))
                            val newRotation = (imported.rotation + spec.rotationDegrees) % 360
                            imported.rotation = if (newRotation < 0) newRotation + 360 else newRotation
                        }
                    }
                    outDoc.save(output)
                }
            }
            output
        }
    }

    suspend fun imagesToPdf(
        imageFiles: List<File>,
        output: File,
        pageSize: PdfPageSizeOption,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageFiles.isNotEmpty()) { "Select at least one image." }
            val pdf = PdfDocument()
            try {
                imageFiles.forEachIndexed { idx, file ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    val sample = if (maxDim > 2000) (maxDim / 1600).coerceAtLeast(1) else 1
                    val bmp = BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    ) ?: throw IllegalStateException("Could not decode ${file.name}")

                    try {
                        val pageW = if (pageSize.widthPt > 0) pageSize.widthPt else bmp.width.coerceIn(300, 1440)
                        val pageH = if (pageSize.heightPt > 0) pageSize.heightPt else bmp.height.coerceIn(300, 1920)
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas
                        canvas.drawColor(Color.WHITE)

                        val margin = if (pageSize == PdfPageSizeOption.ORIGINAL) 0f else 24f
                        val availW = (pageW - margin * 2).coerceAtLeast(1f)
                        val availH = (pageH - margin * 2).coerceAtLeast(1f)
                        val scale = minOf(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val left = (pageW - drawW) / 2f
                        val top = (pageH - drawH) / 2f
                        canvas.drawBitmap(
                            bmp,
                            null,
                            android.graphics.RectF(left, top, left + drawW, top + drawH),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        pdf.finishPage(page)
                    } finally {
                        bmp.recycle()
                    }
                    onProgress(((idx + 1) * 100) / imageFiles.size)
                }
                FileOutputStream(output).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
            output
        }
    }

    suspend fun pdfToImages(
        input: File,
        outputDir: File,
        baseName: String,
        format: ImageOutputFormat,
        onProgress: (Int) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val outFiles = mutableListOf<File>()
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount.coerceAtLeast(1)
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val w = (page.width * 2).coerceIn(600, 2200)
                            val h = (page.height * 2).coerceIn(600, 2800)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val ext = if (format == ImageOutputFormat.PNG) "png" else "jpg"
                            val outFile = File(outputDir, "${baseName}_page_${i + 1}.$ext")
                            FileOutputStream(outFile).use { fos ->
                                val compressFmt = if (format == ImageOutputFormat.PNG) {
                                    Bitmap.CompressFormat.PNG
                                } else {
                                    Bitmap.CompressFormat.JPEG
                                }
                                bmp.compress(compressFmt, 92, fos)
                            }
                            bmp.recycle()
                            outFiles.add(outFile)
                        }
                        onProgress(((i + 1) * 100) / total)
                    }
                }
            }
            outFiles
        }
    }

    suspend fun addWatermarkAndPageNumbers(
        input: File,
        output: File,
        watermarkText: String,
        addPageNumbers: Boolean,
        opacity: Float = 0.28f
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { doc ->
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        if (watermarkText.isNotBlank()) {
                            val gs = PDExtendedGraphicsState().apply {
                                nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.85f)
                            }
                            cs.setGraphicsStateParameters(gs)
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 34f)
                            cs.setNonStrokingColor(36, 132, 93)
                            cs.newLineAtOffset(box.width * 0.18f, box.height * 0.48f)
                            cs.showText(watermarkText.take(40))
                            cs.endText()
                        }
                        if (addPageNumbers) {
                            val gsFull = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.9f }
                            cs.setGraphicsStateParameters(gsFull)
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, 11f)
                            cs.setNonStrokingColor(32, 43, 38)
                            cs.newLineAtOffset(box.width * 0.46f, 22f)
                            cs.showText("${i + 1} / $total")
                            cs.endText()
                        }
                    }
                }
                doc.save(output)
            }
            output
        }
    }

    suspend fun addAnnotationOrSignature(
        input: File,
        output: File,
        pageIndex: Int,
        overlayText: String,
        highlightText: Boolean,
        signatureBitmap: Bitmap? = null,
        overlayImageFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { doc ->
                require(pageIndex in 0 until doc.numberOfPages) { "Invalid page selected." }
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    if (highlightText) {
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(255, 235, 59)
                        cs.addRect(box.width * 0.1f, box.height * 0.72f, box.width * 0.8f, 24f)
                        cs.fill()
                    }
                    if (overlayText.isNotBlank()) {
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1f }
                        cs.setGraphicsStateParameters(gs)
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                        cs.setNonStrokingColor(23, 91, 67)
                        cs.newLineAtOffset(48f, box.height - 64f)
                        cs.showText(overlayText.take(90))
                        cs.endText()
                    }
                    if (signatureBitmap != null) {
                        val pdImage = LosslessFactory.createFromImage(doc, signatureBitmap)
                        cs.drawImage(pdImage, box.width - 190f, 44f, 140f, 64f)
                    }
                    if (overlayImageFile != null && overlayImageFile.exists()) {
                        val bmp = BitmapFactory.decodeFile(overlayImageFile.absolutePath)
                        if (bmp != null) {
                            try {
                                val pdImage = LosslessFactory.createFromImage(doc, bmp)
                                cs.drawImage(pdImage, 48f, 48f, 120f, 120f)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
                doc.save(output)
            }
            output
        }
    }

    suspend fun fillFormFields(
        input: File,
        output: File,
        fieldValues: Map<String, String>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { doc ->
                val acroForm = doc.documentCatalog.acroForm
                    ?: throw IllegalStateException("This PDF does not contain fillable AcroForm fields.")
                fieldValues.forEach { (name, value) ->
                    acroForm.getField(name)?.setValue(value)
                }
                doc.save(output)
            }
            output
        }
    }

    suspend fun updateMetadata(
        input: File,
        output: File,
        title: String,
        author: String,
        subject: String,
        keywords: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { doc ->
                val info = doc.documentInformation
                info.title = title
                info.author = author
                info.subject = subject
                info.keywords = keywords
                doc.documentInformation = info
                doc.save(output)
            }
            output
        }
    }

    suspend fun changePageSizeAndOrientation(
        input: File,
        output: File,
        pageSize: PdfPageSizeOption,
        landscape: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(input).use { doc ->
                val baseRect = when (pageSize) {
                    PdfPageSizeOption.A4 -> PDRectangle.A4
                    PdfPageSizeOption.LETTER -> PDRectangle.LETTER
                    PdfPageSizeOption.LEGAL -> PDRectangle.LEGAL
                    PdfPageSizeOption.ORIGINAL -> null
                }
                for (i in 0 until doc.numberOfPages) {
                    val page: PDPage = doc.getPage(i)
                    val target = baseRect ?: page.mediaBox
                    val finalRect = if (landscape) {
                        PDRectangle(maxOf(target.width, target.height), minOf(target.width, target.height))
                    } else {
                        PDRectangle(minOf(target.width, target.height), maxOf(target.width, target.height))
                    }
                    page.mediaBox = finalRect
                    page.cropBox = finalRect
                }
                doc.save(output)
            }
            output
        }
    }

    suspend fun protectPdfWithPassword(
        input: File,
        output: File,
        userPassword: String,
        ownerPassword: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(userPassword.isNotBlank()) { "Password cannot be blank." }
            PDDocument.load(input).use { doc ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(ownerPassword.ifBlank { userPassword }, userPassword, ap).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(spp)
                doc.save(output)
            }
            output
        }
    }

    suspend fun removePdfPassword(
        input: File,
        output: File,
        authorizedPassword: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(authorizedPassword.isNotBlank()) { "Enter the authorized password to unlock this PDF." }
            PDDocument.load(input, authorizedPassword).use { doc ->
                doc.setAllSecurityToBeRemoved(true)
                doc.save(output)
            }
            output
        }
    }

    suspend fun extractOcrTextFromPdf(
        input: File,
        maxPages: Int = 5,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = StringBuilder()
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pagesToRead = renderer.pageCount.coerceAtMost(maxPages).coerceAtLeast(1)
                    for (i in 0 until pagesToRead) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(
                                (page.width * 2).coerceIn(800, 1800),
                                (page.height * 2).coerceIn(800, 2400),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pageText = OcrEngine.recognizeFromBitmap(bmp).getOrDefault("")
                            bmp.recycle()
                            if (pageText.isNotBlank()) {
                                builder.appendLine("--- Page ${i + 1} ---")
                                builder.appendLine(pageText)
                                builder.appendLine()
                            }
                        }
                        onProgress(((i + 1) * 100) / pagesToRead)
                    }
                }
            }
            builder.toString().trim().ifBlank { "No selectable or recognizable text found on scanned pages." }
        }
    }

    suspend fun extractEmbeddedPdfText(
        input: File,
        maxPages: Int = 15,
        maxChars: Int = 45_000
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(input.exists()) { "PDF file does not exist." }
            PDDocument.load(input).use { doc ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                    startPage = 1
                    endPage = doc.numberOfPages.coerceAtMost(maxPages).coerceAtLeast(1)
                }
                val raw = stripper.getText(doc).trim()
                if (raw.length > maxChars) raw.take(maxChars) else raw
            }
        }
    }

    suspend fun renderPagesForAi(
        input: File,
        maxPages: Int = 3,
        targetWidthPx: Int = 960
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmaps = mutableListOf<Bitmap>()
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1).toFloat()
                            val w = targetWidthPx.coerceAtLeast(320)
                            val h = (page.height * scale).toInt().coerceIn(320, 1600)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
            bitmaps
        }
    }

    fun launchSystemPrint(activityContext: Context, pdfFile: File, jobName: String) {
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Print failed")
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }
}
