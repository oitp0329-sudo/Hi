package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.authentication.AuthRepository
import com.example.core.ai.FileDudeAiRepository
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.FileDudeDatabase
import com.example.core.data.local.PreferencesRepository
import com.example.core.data.remote.CloudSyncRepository
import com.example.core.engines.PdfProcessingEngine
import com.example.core.engines.SmartFileReadyEngine
import com.example.core.engines.VideoProcessingEngine
import com.example.reminders.DocumentWalletRepository
import com.example.reminders.ReminderScheduler
import com.example.subscription.BillingRepository

class AppContainer(application: Application) {
    val database: FileDudeDatabase by lazy { FileDudeDatabase.getInstance(application) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(application) }
    val authRepository: AuthRepository by lazy { AuthRepository(application) }
    val cloudSyncRepository: CloudSyncRepository by lazy { CloudSyncRepository(application) }
    val workspaceFileRepository: WorkspaceFileRepository by lazy {
        WorkspaceFileRepository(
            context = application,
            fileDao = database.workspaceFileDao(),
            taskDao = database.processingTaskDao()
        )
    }
    val documentWalletRepository: DocumentWalletRepository by lazy {
        DocumentWalletRepository(
            dao = database.walletDocumentDao(),
            cloudSyncRepository = cloudSyncRepository
        )
    }
    val pdfEngine: PdfProcessingEngine by lazy { PdfProcessingEngine(application) }
    val videoEngine: VideoProcessingEngine by lazy { VideoProcessingEngine(application) }
    val smartFileReadyEngine: SmartFileReadyEngine by lazy { SmartFileReadyEngine(pdfEngine) }
    val aiRepository: FileDudeAiRepository by lazy { FileDudeAiRepository(application, pdfEngine) }
    val billingRepository: BillingRepository by lazy { BillingRepository(application) }
}

class FileDudeApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.ensureNotificationChannels(this)
        ReminderScheduler.scheduleDailyCheck(this)
        container.billingRepository.connectAndRefresh()
    }
}
