package com.example.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.core.model.VideoExportPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class VideoMetadataInfo(
    val durationMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int,
    val bitrateBps: Long,
    val mimeType: String,
    val sizeBytes: Long
)

data class VideoEditRequest(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val preset: VideoExportPreset = VideoExportPreset.P720,
    val customBitrateBps: Int = 0,
    val rotationDegrees: Float = 0f,
    val cropNormalized: Boolean = false, // Center 1:1 / 16:9 crop toggle
    val muteAudio: Boolean = false,
    val extractAudioOnly: Boolean = false,
    val speedMultiplier: Float = 1f,
    val overlayText: String = "",
    val watermarkBitmapFile: File? = null,
    val replacementAudioFile: File? = null
)

class VideoProcessingEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun inspectVideo(file: File): Result<VideoMetadataInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists()) { "Video file not found." }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
                VideoMetadataInfo(
                    durationMs = duration,
                    widthPx = width,
                    heightPx = height,
                    rotationDegrees = rot,
                    bitrateBps = bitrate,
                    mimeType = mime,
                    sizeBytes = file.length()
                )
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    suspend fun extractThumbnail(file: File, outputJpg: File, timeMs: Long = 1000L): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val frame: Bitmap = retriever.getFrameAtTime(
                        timeMs.coerceAtLeast(0L) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: throw IllegalStateException("Could not extract thumbnail frame from video.")
                    try {
                        FileOutputStream(outputJpg).use { fos ->
                            frame.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                    } finally {
                        frame.recycle()
                    }
                } finally {
                    runCatching { retriever.release() }
                }
                outputJpg
            }
        }

    @OptIn(UnstableApi::class)
    suspend fun exportVideo(
        inputFile: File,
        outputFile: File,
        request: VideoEditRequest,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                if (outputFile.exists()) outputFile.delete()

                val itemBuilder = MediaItem.Builder().setUri(Uri.fromFile(inputFile))
                if (request.trimEndMs > request.trimStartMs && request.trimEndMs > 0L) {
                    itemBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(request.trimStartMs.coerceAtLeast(0L))
                            .setEndPositionMs(request.trimEndMs)
                            .build()
                    )
                }

                val videoEffects = mutableListOf<Effect>()
                if (request.cropNormalized) {
                    videoEffects.add(Crop(-0.75f, 0.75f, -0.75f, 0.75f))
                }
                if (request.rotationDegrees != 0f) {
                    videoEffects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(request.rotationDegrees)
                            .build()
                    )
                }
                if (request.preset.maxHeightPx > 0) {
                    videoEffects.add(Presentation.createForHeight(request.preset.maxHeightPx))
                }

                val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>()
                if (request.overlayText.isNotBlank()) {
                    overlays.add(TextOverlay.createStaticTextOverlay(android.text.SpannableString(request.overlayText.take(40))))
                }
                if (request.watermarkBitmapFile != null && request.watermarkBitmapFile.exists()) {
                    val wmBmp = BitmapFactory.decodeFile(request.watermarkBitmapFile.absolutePath)
                    if (wmBmp != null) {
                        val scaled = Bitmap.createScaledBitmap(wmBmp, 160, 160, true)
                        if (scaled != wmBmp) wmBmp.recycle()
                        overlays.add(BitmapOverlay.createStaticBitmapOverlay(scaled))
                    }
                }
                if (overlays.isNotEmpty()) {
                    videoEffects.add(OverlayEffect(overlays))
                }

                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                if (request.speedMultiplier != 1f) {
                    val sonic = SonicAudioProcessor().apply {
                        setSpeed(request.speedMultiplier.coerceIn(0.5f, 2.0f))
                    }
                    audioProcessors.add(sonic)
                }

                val editedBuilder = EditedMediaItem.Builder(itemBuilder.build())
                    .setRemoveAudio(request.muteAudio || request.replacementAudioFile != null)
                    .setRemoveVideo(request.extractAudioOnly)
                    .setEffects(Effects(audioProcessors, videoEffects))

                val editedItem = editedBuilder.build()

                val encoderBuilder = DefaultEncoderFactory.Builder(context)
                val targetBitrate = when {
                    request.customBitrateBps > 0 -> request.customBitrateBps
                    request.preset.targetBitrateBps > 0 -> request.preset.targetBitrateBps
                    else -> 0
                }
                if (targetBitrate > 0) {
                    encoderBuilder.setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrate)
                            .build()
                    )
                }

                val transformerBuilder = Transformer.Builder(context)
                    .setEncoderFactory(encoderBuilder.build())
                if (!request.extractAudioOnly) {
                    transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H264)
                }

                var finished = false
                val progressHolder = ProgressHolder()
                lateinit var progressRunnable: Runnable

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished = true
                        mainHandler.removeCallbacks(progressRunnable)
                        onProgress(100)
                        if (continuation.isActive) {
                            continuation.resume(Result.success(outputFile))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finished = true
                        mainHandler.removeCallbacks(progressRunnable)
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exportException))
                        }
                    }
                }

                val transformer = transformerBuilder.addListener(listener).build()

                progressRunnable = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED && !finished) {
                            mainHandler.postDelayed(this, 400L)
                        }
                    }
                }

                val replacementAudio = request.replacementAudioFile
                if (replacementAudio != null && replacementAudio.exists() && !request.extractAudioOnly) {
                    val videoSeq = EditedMediaItemSequence.Builder(editedItem).build()
                    val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(replacementAudio)))
                        .setRemoveVideo(true)
                        .build()
                    val audioSeq = EditedMediaItemSequence.Builder(audioItem).build()
                    val composition = Composition.Builder(listOf(videoSeq, audioSeq)).build()
                    transformer.start(composition, outputFile.absolutePath)
                } else {
                    transformer.start(editedItem, outputFile.absolutePath)
                }

                mainHandler.post(progressRunnable)

                continuation.invokeOnCancellation {
                    finished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post { runCatching { transformer.cancel() } }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun mergeVideos(
        inputs: List<File>,
        outputFile: File,
        preset: VideoExportPreset,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                require(inputs.size >= 2) { "Select at least two videos to merge." }
                if (outputFile.exists()) outputFile.delete()

                val videoEffects = if (preset.maxHeightPx > 0) {
                    listOf(Presentation.createForHeight(preset.maxHeightPx))
                } else emptyList()

                val editedItems = inputs.map { file ->
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                        .setEffects(Effects(emptyList(), videoEffects))
                        .build()
                }
                val sequence = EditedMediaItemSequence.Builder(editedItems).build()
                val composition = Composition.Builder(listOf(sequence)).build()

                var finished = false
                val progressHolder = ProgressHolder()
                lateinit var progressRunnable: Runnable

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(c: Composition, result: ExportResult) {
                            finished = true
                            mainHandler.removeCallbacks(progressRunnable)
                            onProgress(100)
                            if (continuation.isActive) continuation.resume(Result.success(outputFile))
                        }

                        override fun onError(c: Composition, result: ExportResult, e: ExportException) {
                            finished = true
                            mainHandler.removeCallbacks(progressRunnable)
                            if (continuation.isActive) continuation.resume(Result.failure(e))
                        }
                    })
                    .build()

                progressRunnable = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED && !finished) {
                            mainHandler.postDelayed(this, 400L)
                        }
                    }
                }

                transformer.start(composition, outputFile.absolutePath)
                mainHandler.post(progressRunnable)

                continuation.invokeOnCancellation {
                    finished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post { runCatching { transformer.cancel() } }
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(Result.failure(e))
            }
        }
    }
}
