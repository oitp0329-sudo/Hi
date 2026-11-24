package com.example.core.data.files

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.core.data.local.ProcessingTaskDao
import com.example.core.data.local.ProcessingTaskEntity
import com.example.core.data.local.WorkspaceFileDao
import com.example.core.data.local.WorkspaceFileEntity
import com.example.core.model.FileCategory
import com.example.core.model.ProcessingTaskRecord
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class StorageOverviewStats(
    val totalBytes: Long,
    val pdfBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val otherBytes: Long,
    val cacheBytes: Long,
    val fileCount: Int
)

class WorkspaceFileRepository(
    private val context: Context,
    private val fileDao: WorkspaceFileDao,
    private val taskDao: ProcessingTaskDao
) {
    private val workspaceDir: File
        get() = File(context.filesDir, "workspace").apply { if (!exists()) mkdirs() }

    private val walletDir: File
        get() = File(context.filesDir, "wallet").apply { if (!exists()) mkdirs() }

    private val exportCacheDir: File
        get() = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }

    val activeFiles: Flow<List<WorkspaceFile>> =
        fileDao.observeActiveFiles().map { list -> list.map { it.toDomain() } }

    val trashFiles: Flow<List<WorkspaceFile>> =
        fileDao.observeTrashFiles().map { list -> list.map { it.toDomain() } }

    val favouriteFiles: Flow<List<WorkspaceFile>> =
        fileDao.observeFavourites().map { list -> list.map { it.toDomain() } }

    val recentTasks: Flow<List<ProcessingTaskRecord>> =
        taskDao.observeRecentTasks().map { list -> list.map { it.toDomain() } }

    suspend fun importUriToWorkspace(
        uri: Uri,
        folderName: String = "Workspace"
    ): Result<WorkspaceFile> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val originalName = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
            val sanitizedName = originalName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            val uniqueName = "${System.currentTimeMillis()}_$sanitizedName"
            val destFile = File(workspaceDir, uniqueName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")

            val fileModel = buildMetadataForFile(
                file = destFile,
                displayName = originalName,
                mimeType = mimeType,
                folderName = folderName
            )
            fileDao.upsert(WorkspaceFileEntity.fromDomain(fileModel))
            fileModel
        }
    }

    suspend fun importAttachmentForWallet(uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = queryDisplayName(uri) ?: "attachment_${System.currentTimeMillis()}"
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                val dest = File(walletDir, "${System.currentTimeMillis()}_$safeName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to open selected attachment.")
                dest.absolutePath to displayName
            }
        }

    suspend fun registerProcessedOutput(
        outputFile: File,
        displayName: String,
        mimeType: String,
        folderName: String = "Processed",
        toolName: String,
        inputName: String,
        originalSizeBytes: Long
    ): WorkspaceFile = withContext(Dispatchers.IO) {
        val permanentName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")}"
        val permanentFile = File(workspaceDir, permanentName)
        if (outputFile.absolutePath != permanentFile.absolutePath) {
            outputFile.copyTo(permanentFile, overwrite = true)
        }
        val model = buildMetadataForFile(
            file = permanentFile,
            displayName = displayName,
            mimeType = mimeType,
            folderName = folderName
        )
        fileDao.upsert(WorkspaceFileEntity.fromDomain(model))
        taskDao.insert(
            ProcessingTaskEntity.fromDomain(
                ProcessingTaskRecord(
                    toolName = toolName,
                    inputName = inputName,
                    outputName = displayName,
                    outputPath = permanentFile.absolutePath,
                    status = "COMPLETED",
                    savedBytes = (originalSizeBytes - model.sizeBytes).coerceAtLeast(0L)
                )
            )
        )
        model
    }

    suspend fun recordTaskEvent(
        toolName: String,
        inputName: String,
        outputName: String,
        status: String
    ) {
        taskDao.insert(
            ProcessingTaskEntity.fromDomain(
                ProcessingTaskRecord(
                    toolName = toolName,
                    inputName = inputName,
                    outputName = outputName,
                    status = status
                )
            )
        )
    }

    suspend fun exportToUri(sourcePath: String, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = File(sourcePath)
                require(source.exists()) { "Source file no longer exists." }
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    FileInputStream(source).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Could not open destination for writing.")
                Unit
            }
        }

    fun createShareIntent(filePath: String, mimeType: String, title: String): Intent {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, title)
    }

    fun createTempExportFile(fileName: String): File {
        val safe = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        return File(exportCacheDir, "${UUID.randomUUID().toString().take(8)}_$safe")
    }

    suspend fun toggleFavourite(id: String, currentFav: Boolean) {
        fileDao.setFavourite(id, !currentFav)
    }

    suspend fun renameFile(file: WorkspaceFile, newDisplayName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = newDisplayName.trim()
                require(trimmed.isNotEmpty()) { "Filename cannot be empty." }
                val oldFile = File(file.localPath)
                val safeDiskName = "${System.currentTimeMillis()}_${trimmed.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")}"
                val renamedFile = File(oldFile.parentFile ?: workspaceDir, safeDiskName)
                if (oldFile.exists()) {
                    oldFile.renameTo(renamedFile)
                }
                fileDao.rename(file.id, trimmed, renamedFile.absolutePath)
            }
        }

    suspend fun copyFile(file: WorkspaceFile, targetFolder: String = file.folderName): Result<WorkspaceFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(file.localPath)
                require(src.exists()) { "Source file not found." }
                val copyName = "Copy of ${file.displayName}"
                val dest = File(workspaceDir, "${System.currentTimeMillis()}_copy_${src.name}")
                src.copyTo(dest, overwrite = true)
                val copied = file.copy(
                    id = UUID.randomUUID().toString(),
                    localPath = dest.absolutePath,
                    displayName = copyName,
                    folderName = targetFolder,
                    createdAtMillis = System.currentTimeMillis(),
                    lastAccessedAtMillis = System.currentTimeMillis()
                )
                fileDao.upsert(WorkspaceFileEntity.fromDomain(copied))
                copied
            }
        }

    suspend fun moveFileToFolder(id: String, folderName: String) {
        fileDao.moveToFolder(id, folderName.trim().ifBlank { "Workspace" })
    }

    suspend fun moveToTrash(id: String) {
        fileDao.setTrashed(id, true, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: String) {
        fileDao.setTrashed(id, false, System.currentTimeMillis())
    }

    suspend fun deletePermanently(file: WorkspaceFile) = withContext(Dispatchers.IO) {
        File(file.localPath).delete()
        fileDao.deletePermanently(file.id)
    }

    suspend fun emptyTrash(trashList: List<WorkspaceFile>) = withContext(Dispatchers.IO) {
        trashList.forEach { File(it.localPath).delete() }
        fileDao.emptyTrash()
    }

    suspend fun computeStorageOverview(files: List<WorkspaceFile>): StorageOverviewStats =
        withContext(Dispatchers.IO) {
            var pdf = 0L
            var img = 0L
            var vid = 0L
            var doc = 0L
            for (f in files) {
                when (f.category) {
                    FileCategory.PDF -> pdf += f.sizeBytes
                    FileCategory.IMAGE -> img += f.sizeBytes
                    FileCategory.VIDEO -> vid += f.sizeBytes
                    else -> doc += f.sizeBytes
                }
            }
            val cacheBytes = exportCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            StorageOverviewStats(
                totalBytes = pdf + img + vid + doc,
                pdfBytes = pdf,
                imageBytes = img,
                videoBytes = vid,
                otherBytes = doc,
                cacheBytes = cacheBytes,
                fileCount = files.size
            )
        }

    suspend fun clearTemporaryCache(): Long = withContext(Dispatchers.IO) {
        var cleared = 0L
        exportCacheDir.listFiles()?.forEach { f ->
            if (f.isFile) {
                cleared += f.length()
                f.delete()
            }
        }
        cleared
    }

    fun findDuplicates(files: List<WorkspaceFile>): List<List<WorkspaceFile>> {
        return files.filter { it.sha256.isNotBlank() }
            .groupBy { it.sha256 }
            .values
            .filter { it.size > 1 }
    }

    fun findLargeFiles(files: List<WorkspaceFile>, thresholdBytes: Long = 5L * 1024L * 1024L): List<WorkspaceFile> {
        return files.filter { it.sizeBytes >= thresholdBytes }.sortedByDescending { it.sizeBytes }
    }

    private fun buildMetadataForFile(
        file: File,
        displayName: String,
        mimeType: String,
        folderName: String
    ): WorkspaceFile {
        val lowerName = displayName.lowercase()
        val category = when {
            mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> FileCategory.PDF
            mimeType.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".bmp") -> FileCategory.IMAGE
            mimeType.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") ||
                lowerName.endsWith(".mkv") -> FileCategory.VIDEO
            else -> FileCategory.DOCUMENT
        }

        var width = 0
        var height = 0
        var pages = 0
        var duration = 0L

        when (category) {
            FileCategory.IMAGE -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                width = opts.outWidth.coerceAtLeast(0)
                height = opts.outHeight.coerceAtLeast(0)
            }
            FileCategory.PDF -> {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pages = renderer.pageCount
                        }
                    }
                }
            }
            FileCategory.VIDEO -> {
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(file.absolutePath)
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                }
                runCatching { retriever.release() }
            }
            else -> Unit
        }

        return WorkspaceFile(
            localPath = file.absolutePath,
            displayName = displayName,
            mimeType = mimeType,
            category = category,
            sizeBytes = file.length(),
            widthPx = width,
            heightPx = height,
            pageCount = pages,
            durationMs = duration,
            folderName = folderName,
            sha256 = computeSha256(file)
        )
    }

    private fun computeSha256(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }
}
