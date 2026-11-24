package com.example.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.FileCategory
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.ProcessingTaskRecord
import com.example.core.model.SmartPreset
import com.example.core.model.SmartTargetFormat
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import com.example.core.model.WorkspaceFile

@Entity(tableName = "workspace_files")
data class WorkspaceFileEntity(
    @PrimaryKey val id: String,
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val categoryName: String,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val pageCount: Int,
    val durationMs: Long,
    val folderName: String,
    val isFavourite: Boolean,
    val isInTrash: Boolean,
    val sha256: String,
    val createdAtMillis: Long,
    val lastAccessedAtMillis: Long
) {
    fun toDomain(): WorkspaceFile = WorkspaceFile(
        id = id,
        localPath = localPath,
        displayName = displayName,
        mimeType = mimeType,
        category = FileCategory.entries.firstOrNull { it.name == categoryName } ?: FileCategory.DOCUMENT,
        sizeBytes = sizeBytes,
        widthPx = widthPx,
        heightPx = heightPx,
        pageCount = pageCount,
        durationMs = durationMs,
        folderName = folderName,
        isFavourite = isFavourite,
        isInTrash = isInTrash,
        sha256 = sha256,
        createdAtMillis = createdAtMillis,
        lastAccessedAtMillis = lastAccessedAtMillis
    )

    companion object {
        fun fromDomain(model: WorkspaceFile) = WorkspaceFileEntity(
            id = model.id,
            localPath = model.localPath,
            displayName = model.displayName,
            mimeType = model.mimeType,
            categoryName = model.category.name,
            sizeBytes = model.sizeBytes,
            widthPx = model.widthPx,
            heightPx = model.heightPx,
            pageCount = model.pageCount,
            durationMs = model.durationMs,
            folderName = model.folderName,
            isFavourite = model.isFavourite,
            isInTrash = model.isInTrash,
            sha256 = model.sha256,
            createdAtMillis = model.createdAtMillis,
            lastAccessedAtMillis = model.lastAccessedAtMillis
        )
    }
}

@Entity(tableName = "wallet_documents")
data class WalletDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryName: String,
    val attachmentPath: String?,
    val attachmentName: String?,
    val notes: String,
    val issueDateMillis: Long?,
    val expiryDateMillis: Long,
    val reminderOffsetsCsv: String,
    val isRenewed: Boolean,
    val lastRenewedAtMillis: Long?,
    val renewalHistoryCsv: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): WalletDocument = WalletDocument(
        id = id,
        title = title,
        category = WalletCategory.fromRaw(categoryName),
        attachmentPath = attachmentPath,
        attachmentName = attachmentName,
        notes = notes,
        issueDateMillis = issueDateMillis,
        expiryDateMillis = expiryDateMillis,
        reminderOffsetsDays = reminderOffsetsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(90, 30, 7, 1) },
        isRenewed = isRenewed,
        lastRenewedAtMillis = lastRenewedAtMillis,
        renewalHistory = renewalHistoryCsv.split("||").filter { it.isNotBlank() },
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(model: WalletDocument) = WalletDocumentEntity(
            id = model.id,
            title = model.title,
            categoryName = model.category.name,
            attachmentPath = model.attachmentPath,
            attachmentName = model.attachmentName,
            notes = model.notes,
            issueDateMillis = model.issueDateMillis,
            expiryDateMillis = model.expiryDateMillis,
            reminderOffsetsCsv = model.reminderOffsetsDays.distinct().sortedDescending().joinToString(","),
            isRenewed = model.isRenewed,
            lastRenewedAtMillis = model.lastRenewedAtMillis,
            renewalHistoryCsv = model.renewalHistory.joinToString("||"),
            createdAtMillis = model.createdAtMillis,
            updatedAtMillis = model.updatedAtMillis
        )
    }
}

@Entity(tableName = "smart_presets")
data class SmartPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val targetFormatName: String,
    val maxSizeBytes: Long,
    val minWidthPx: Int,
    val maxWidthPx: Int,
    val minHeightPx: Int,
    val maxHeightPx: Int,
    val aspectRatioLabel: String,
    val pdfPageSizeName: String,
    val qualityPercent: Int
) {
    fun toDomain(): SmartPreset = SmartPreset(
        id = id,
        name = name,
        description = description,
        isBuiltIn = isBuiltIn,
        targetFormat = SmartTargetFormat.fromRaw(targetFormatName),
        maxSizeBytes = maxSizeBytes,
        minWidthPx = minWidthPx,
        maxWidthPx = maxWidthPx,
        minHeightPx = minHeightPx,
        maxHeightPx = maxHeightPx,
        aspectRatioLabel = aspectRatioLabel,
        pdfPageSize = PdfPageSizeOption.fromRaw(pdfPageSizeName),
        qualityPercent = qualityPercent
    )

    companion object {
        fun fromDomain(model: SmartPreset) = SmartPresetEntity(
            id = model.id,
            name = model.name,
            description = model.description,
            isBuiltIn = model.isBuiltIn,
            targetFormatName = model.targetFormat.name,
            maxSizeBytes = model.maxSizeBytes,
            minWidthPx = model.minWidthPx,
            maxWidthPx = model.maxWidthPx,
            minHeightPx = model.minHeightPx,
            maxHeightPx = model.maxHeightPx,
            aspectRatioLabel = model.aspectRatioLabel,
            pdfPageSizeName = model.pdfPageSize.name,
            qualityPercent = model.qualityPercent
        )
    }
}

@Entity(tableName = "processing_tasks")
data class ProcessingTaskEntity(
    @PrimaryKey val id: String,
    val toolName: String,
    val inputName: String,
    val outputName: String,
    val outputPath: String?,
    val status: String,
    val savedBytes: Long,
    val createdAtMillis: Long
) {
    fun toDomain(): ProcessingTaskRecord = ProcessingTaskRecord(
        id = id,
        toolName = toolName,
        inputName = inputName,
        outputName = outputName,
        outputPath = outputPath,
        status = status,
        savedBytes = savedBytes,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromDomain(model: ProcessingTaskRecord) = ProcessingTaskEntity(
            id = model.id,
            toolName = model.toolName,
            inputName = model.inputName,
            outputName = model.outputName,
            outputPath = model.outputPath,
            status = model.status,
            savedBytes = model.savedBytes,
            createdAtMillis = model.createdAtMillis
        )
    }
}
