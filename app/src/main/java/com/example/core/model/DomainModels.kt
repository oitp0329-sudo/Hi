package com.example.core.model

import java.util.UUID

enum class AppThemeMode(val label: String) {
    LIGHT("Light (Default)"),
    DARK("Dark"),
    SYSTEM("System Default")
}

enum class PlanTier {
    FREE,
    PREMIUM
}

enum class FileCategory(val label: String) {
    ALL("All Files"),
    PDF("PDFs"),
    IMAGE("Images"),
    VIDEO("Videos"),
    DOCUMENT("Documents")
}

enum class WalletCategory(val label: String) {
    PASSPORT("Passport"),
    DRIVING_LICENCE("Driving Licence"),
    IDENTITY("Identity Documents"),
    INSURANCE("Insurance"),
    CERTIFICATES("Certificates"),
    EDUCATIONAL("Educational Documents"),
    WARRANTY("Warranty"),
    SUBSCRIPTIONS("Subscriptions"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String): WalletCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class QuickActionId(
    val title: String,
    val subtitle: String,
    val routeKey: String
) {
    COMPRESS_PDF("Compress PDF", "Shrink size", "pdf:compress"),
    MERGE_PDF("Merge PDF", "Combine files", "pdf:merge"),
    CONVERT_IMAGE("Convert Image", "JPG, PNG, WebP", "image:convert"),
    RESIZE_IMAGE("Resize Image", "Pixels or %", "image:resize"),
    COMPRESS_IMAGE("Compress Image", "Target KB / MB", "image:compress"),
    IMAGE_TO_PDF("Image to PDF", "Scan to PDF", "pdf:img2pdf"),
    EDIT_VIDEO("Edit Video", "Trim & export", "video:trim"),
    EXTRACT_TEXT("Extract Text", "On-device OCR", "image:ocr");

    companion object {
        fun fromRaw(raw: String): QuickActionId? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class ImageOutputFormat(val label: String, val extension: String, val mimeType: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
    BMP("BMP", "bmp", "image/bmp")
}

enum class SmartTargetFormat(val label: String, val extension: String, val mimeType: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
    PDF("PDF", "pdf", "application/pdf");

    companion object {
        fun fromRaw(raw: String): SmartTargetFormat =
            entries.firstOrNull { it.name == raw } ?: JPG
    }
}

enum class PdfPageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    ORIGINAL("Original Size", 0, 0),
    A4("A4 (595 × 842 pt)", 595, 842),
    LETTER("US Letter (612 × 792 pt)", 612, 792),
    LEGAL("Legal (612 × 1008 pt)", 612, 1008);

    companion object {
        fun fromRaw(raw: String): PdfPageSizeOption =
            entries.firstOrNull { it.name == raw } ?: A4
    }
}

enum class VideoExportPreset(val label: String, val maxHeightPx: Int, val targetBitrateBps: Int) {
    P360("360p (Compact)", 360, 800_000),
    P480("480p (Standard)", 480, 1_500_000),
    P720("720p (HD)", 720, 3_500_000),
    P1080("1080p (Full HD)", 1080, 6_500_000),
    ORIGINAL("Original Quality", 0, 0)
}

enum class FileSortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name A–Z"),
    SIZE_DESC("Largest first")
}

sealed interface ToolExecutionState<out T> {
    data object Idle : ToolExecutionState<Nothing>
    data class Ready(val description: String) : ToolExecutionState<Nothing>
    data class Processing(
        val stage: String,
        val progressPercent: Int? = null,
        val canCancel: Boolean = true
    ) : ToolExecutionState<Nothing>
    data class Success<T>(val result: T) : ToolExecutionState<T>
    data class Error(val title: String, val message: String, val isCapabilityLimitation: Boolean = false) :
        ToolExecutionState<Nothing>
}

data class WorkspaceFile(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val category: FileCategory,
    val sizeBytes: Long,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val pageCount: Int = 0,
    val durationMs: Long = 0L,
    val folderName: String = "Workspace",
    val isFavourite: Boolean = false,
    val isInTrash: Boolean = false,
    val sha256: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastAccessedAtMillis: Long = System.currentTimeMillis()
)

data class WalletDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: WalletCategory,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val notes: String = "",
    val issueDateMillis: Long? = null,
    val expiryDateMillis: Long,
    val reminderOffsetsDays: List<Int> = listOf(90, 30, 7, 1),
    val isRenewed: Boolean = false,
    val lastRenewedAtMillis: Long? = null,
    val renewalHistory: List<String> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class SmartPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = false,
    val targetFormat: SmartTargetFormat = SmartTargetFormat.JPG,
    val maxSizeBytes: Long = 200 * 1024L,
    val minWidthPx: Int = 0,
    val maxWidthPx: Int = 0,
    val minHeightPx: Int = 0,
    val maxHeightPx: Int = 0,
    val aspectRatioLabel: String = "",
    val pdfPageSize: PdfPageSizeOption = PdfPageSizeOption.ORIGINAL,
    val qualityPercent: Int = 88
)

data class ValidationCheckItem(
    val ruleName: String,
    val expected: String,
    val actual: String,
    val passed: Boolean
)

data class SmartReadyResult(
    val outputPath: String,
    val outputName: String,
    val outputMimeType: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val outputWidthPx: Int,
    val outputHeightPx: Int,
    val outputPageCount: Int,
    val checks: List<ValidationCheckItem>,
    val allPassed: Boolean
)

data class ProcessingTaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val inputName: String,
    val outputName: String,
    val outputPath: String? = null,
    val status: String, // COMPLETED, FAILED, CANCELLED
    val savedBytes: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class SubscriptionProductUi(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String?,
    val billingPeriodLabel: String,
    val isConfiguredInPlay: Boolean
)
