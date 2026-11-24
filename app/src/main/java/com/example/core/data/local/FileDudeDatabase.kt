package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.SmartPreset
import com.example.core.model.SmartTargetFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        WorkspaceFileEntity::class,
        WalletDocumentEntity::class,
        SmartPresetEntity::class,
        ProcessingTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FileDudeDatabase : RoomDatabase() {
    abstract fun workspaceFileDao(): WorkspaceFileDao
    abstract fun walletDocumentDao(): WalletDocumentDao
    abstract fun smartPresetDao(): SmartPresetDao
    abstract fun processingTaskDao(): ProcessingTaskDao

    companion object {
        @Volatile
        private var INSTANCE: FileDudeDatabase? = null

        fun getInstance(context: Context): FileDudeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FileDudeDatabase::class.java,
                    "filedude_local.db"
                ).build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    seedBuiltInPresets(instance.smartPresetDao())
                }
                instance
            }
        }

        private suspend fun seedBuiltInPresets(dao: SmartPresetDao) {
            val defaults = listOf(
                SmartPreset(
                    id = "builtin_passport_photo",
                    name = "Passport Photo",
                    description = "Configurable portrait ratio (e.g., 35:45 / 2×2 in) under 200 KB JPG. Verify your issuing authority's latest rules.",
                    isBuiltIn = true,
                    targetFormat = SmartTargetFormat.JPG,
                    maxSizeBytes = 200 * 1024L,
                    minWidthPx = 413,
                    maxWidthPx = 1200,
                    minHeightPx = 531,
                    maxHeightPx = 1500,
                    aspectRatioLabel = "35:45",
                    pdfPageSize = PdfPageSizeOption.ORIGINAL,
                    qualityPercent = 90
                ),
                SmartPreset(
                    id = "builtin_job_application",
                    name = "Job Application PDF",
                    description = "A4 PDF compressed cleanly under 2 MB for HR portals and ATS uploads.",
                    isBuiltIn = true,
                    targetFormat = SmartTargetFormat.PDF,
                    maxSizeBytes = 2L * 1024L * 1024L,
                    minWidthPx = 0,
                    maxWidthPx = 0,
                    minHeightPx = 0,
                    maxHeightPx = 0,
                    aspectRatioLabel = "",
                    pdfPageSize = PdfPageSizeOption.A4,
                    qualityPercent = 82
                ),
                SmartPreset(
                    id = "builtin_college_admission",
                    name = "College Admission",
                    description = "Compact A4 PDF under 500 KB for transcripts, certificates, and admission portals.",
                    isBuiltIn = true,
                    targetFormat = SmartTargetFormat.PDF,
                    maxSizeBytes = 500L * 1024L,
                    minWidthPx = 0,
                    maxWidthPx = 0,
                    minHeightPx = 0,
                    maxHeightPx = 0,
                    aspectRatioLabel = "",
                    pdfPageSize = PdfPageSizeOption.A4,
                    qualityPercent = 76
                ),
                SmartPreset(
                    id = "builtin_govt_form",
                    name = "Government Form",
                    description = "Strict 100 KB JPG scan with readable dimensions for portal uploads.",
                    isBuiltIn = true,
                    targetFormat = SmartTargetFormat.JPG,
                    maxSizeBytes = 100L * 1024L,
                    minWidthPx = 300,
                    maxWidthPx = 1000,
                    minHeightPx = 300,
                    maxHeightPx = 1400,
                    aspectRatioLabel = "",
                    pdfPageSize = PdfPageSizeOption.ORIGINAL,
                    qualityPercent = 80
                ),
                SmartPreset(
                    id = "builtin_email_attachment",
                    name = "Email Attachment",
                    description = "Web-ready compressed PDF under 5 MB that opens reliably on any client.",
                    isBuiltIn = true,
                    targetFormat = SmartTargetFormat.PDF,
                    maxSizeBytes = 5L * 1024L * 1024L,
                    minWidthPx = 0,
                    maxWidthPx = 0,
                    minHeightPx = 0,
                    maxHeightPx = 0,
                    aspectRatioLabel = "",
                    pdfPageSize = PdfPageSizeOption.ORIGINAL,
                    qualityPercent = 85
                )
            )
            dao.insertAllIgnore(defaults.map { SmartPresetEntity.fromDomain(it) })
        }
    }
}
