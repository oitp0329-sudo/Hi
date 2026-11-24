package com.example.core.engines

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object OcrEngine {
    suspend fun recognizeFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).await()
                result.text.trim()
            } finally {
                recognizer.close()
            }
        }
    }

    suspend fun recognizeFromImageFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists()) { "Image file not found." }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            val sample = if (maxDim > 2200) (maxDim / 1800).coerceAtLeast(1) else 1
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                ?: throw IllegalStateException("Unable to decode image for OCR.")
            try {
                recognizeFromBitmap(bitmap).getOrThrow()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
