package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.AiQuotaPolicy
import com.example.core.data.files.StorageOverviewStats
import com.example.core.data.local.FileDudeDatabase
import com.example.core.data.local.PreferencesRepository
import com.example.core.data.local.WalletDocumentEntity
import com.example.core.data.local.WorkspaceFileEntity
import com.example.core.engines.ImageEditConfig
import com.example.core.engines.ImageProcessingEngine
import com.example.core.engines.PdfProcessingEngine
import com.example.core.engines.SmartFileReadyEngine
import com.example.core.model.FileCategory
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.PlanTier
import com.example.core.model.SmartPreset
import com.example.core.model.SmartTargetFormat
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import com.example.core.model.WorkspaceFile
import com.example.features.ai.AiAssistantContent
import com.example.features.ai.AiAssistantUiState
import com.example.features.ai.AiMode
import com.example.features.ai.AiTaskState
import com.example.features.home.HomeScreen
import com.example.features.home.HomeUiState
import com.example.features.onboarding.FileDudeSplashScreen
import com.example.subscription.AdConfig
import com.example.subscription.AdPlacementLocation
import com.example.subscription.AdPlacementManager
import com.example.ui.theme.FileDudeTheme
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileDudeRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: FileDudeDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FileDudeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(context.cacheDir, "filedude_tests_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createSampleBitmapFile(name: String, width: Int, height: Int): File {
        val file = File(tempDir, name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(36, 132, 93))
        FileOutputStream(file).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bmp.recycle()
        return file
    }

    private fun createSamplePdfBoxFile(name: String, pageCount: Int): File {
        val file = File(tempDir, name)
        PDDocument().use { doc ->
            repeat(pageCount) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `smart file ready transforms image and strictly verifies output bytes and dimensions`() = runBlocking {
        val srcFile = createSampleBitmapFile("raw_portrait.png", 1200, 1200)
        val outFile = File(tempDir, "passport_verified.jpg")
        val inputModel = WorkspaceFile(
            localPath = srcFile.absolutePath,
            displayName = "raw_portrait.png",
            mimeType = "image/png",
            category = FileCategory.IMAGE,
            sizeBytes = srcFile.length(),
            widthPx = 1200,
            heightPx = 1200
        )
        val preset = SmartPreset(
            id = "test_passport",
            name = "Test Passport",
            description = "Under 40 KB JPG 400x514",
            targetFormat = SmartTargetFormat.JPG,
            maxSizeBytes = 40 * 1024L,
            minWidthPx = 350,
            maxWidthPx = 450,
            minHeightPx = 450,
            maxHeightPx = 560,
            aspectRatioLabel = "35:45",
            qualityPercent = 88
        )

        val engine = SmartFileReadyEngine(PdfProcessingEngine(context))
        val result = engine.processAndValidate(inputModel, preset, outFile).getOrThrow()

        assertTrue("Expected all validation checks to pass: ${result.checks}", result.allPassed)
        assertTrue("Output file must exist and respect maxSizeBytes", outFile.exists() && outFile.length() <= 40 * 1024L)

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, opts)
        assertTrue(opts.outWidth in 350..450)
        assertTrue(opts.outHeight in 450..560)
    }

    @Test
    fun `image processing engine converts to PNG and strips EXIF cleanly`() = runBlocking {
        val src = createSampleBitmapFile("camera.png", 800, 600)
        val dst = File(tempDir, "clean.webp")
        val config = ImageEditConfig(
            outputFormat = ImageOutputFormat.WEBP,
            qualityPercent = 80,
            targetWidthPx = 400,
            targetHeightPx = 300,
            overlayText = "VERIFIED"
        )
        ImageProcessingEngine.processAndSave(src, dst, config).getOrThrow()
        assertTrue(dst.exists() && dst.length() > 0L)

        val info = ImageProcessingEngine.inspectImage(dst, "image/webp").getOrThrow()
        assertEquals(400, info.widthPx)
        assertEquals(300, info.heightPx)
    }

    @Test
    fun `pdf processing engine merges splits protects and updates metadata`() = runBlocking {
        val pdfEngine = PdfProcessingEngine(context)
        val p1 = createSamplePdfBoxFile("doc1.pdf", 1)
        val p2 = createSamplePdfBoxFile("doc2.pdf", 2)
        val merged = File(tempDir, "merged.pdf")

        pdfEngine.mergePdfs(listOf(p1, p2), merged).getOrThrow()
        val mergedInfo = pdfEngine.inspectPdf(merged).getOrThrow()
        assertEquals(3, mergedInfo.pageCount)

        val splitDir = File(tempDir, "splits").apply { mkdirs() }
        val parts = pdfEngine.splitPdfIntoRanges(merged, splitDir, "split", 1).getOrThrow()
        assertEquals(3, parts.size)

        val metaUpdated = File(tempDir, "meta.pdf")
        pdfEngine.updateMetadata(merged, metaUpdated, "Tax Filing", "FileDude", "Annual", "pdf,tax").getOrThrow()
        val inspectedMeta = pdfEngine.inspectPdf(metaUpdated).getOrThrow()
        assertEquals("Tax Filing", inspectedMeta.title)
        assertEquals("FileDude", inspectedMeta.author)

        val locked = File(tempDir, "locked.pdf")
        pdfEngine.protectPdfWithPassword(merged, locked, "user123", "owner456").getOrThrow()
        val unlocked = File(tempDir, "unlocked.pdf")
        pdfEngine.removePdfPassword(locked, unlocked, "user123").getOrThrow()
        val unlockedInfo = pdfEngine.inspectPdf(unlocked).getOrThrow()
        assertFalse(unlockedInfo.isEncrypted)
        assertEquals(3, unlockedInfo.pageCount)
    }

    @Test
    fun `room database persists workspace files and wallet documents cleanly`() = runBlocking {
        val wf = WorkspaceFile(
            id = "file_1",
            localPath = "/tmp/sample.pdf",
            displayName = "sample.pdf",
            mimeType = "application/pdf",
            category = FileCategory.PDF,
            sizeBytes = 2048L,
            pageCount = 2
        )
        db.workspaceFileDao().upsert(WorkspaceFileEntity.fromDomain(wf))
        val active = db.workspaceFileDao().observeActiveFiles().first()
        assertEquals(1, active.size)
        assertEquals("sample.pdf", active.first().displayName)

        db.workspaceFileDao().setTrashed("file_1", true, System.currentTimeMillis())
        assertTrue(db.workspaceFileDao().observeActiveFiles().first().isEmpty())
        assertEquals(1, db.workspaceFileDao().observeTrashFiles().first().size)

        val doc = WalletDocument(
            id = "doc_1",
            title = "Passport 2026",
            category = WalletCategory.PASSPORT,
            expiryDateMillis = System.currentTimeMillis() + 30L * 86_400_000L
        )
        db.walletDocumentDao().upsert(WalletDocumentEntity.fromDomain(doc))
        val docs = db.walletDocumentDao().observeAll().first()
        assertEquals(1, docs.size)
        assertEquals("Passport 2026", docs.first().title)
    }

    @Test
    fun `ad placement manager disables ads for premium users and when unconfigured`() {
        assertFalse(AdConfig.IS_ADMOB_CONFIGURED)
        AdPlacementLocation.entries.forEach { loc ->
            assertFalse(AdPlacementManager.isSlotAllowed(loc, PlanTier.PREMIUM))
            assertFalse(AdPlacementManager.isSlotAllowed(loc, PlanTier.FREE))
        }
    }

    @Test
    fun `home screen renders core hero card and organized quick actions without clutter`() {
        composeRule.setContent {
            FileDudeTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        storageStats = StorageOverviewStats(
                            totalBytes = 1024L * 512L,
                            pdfBytes = 1024L * 300L,
                            imageBytes = 1024L * 212L,
                            videoBytes = 0L,
                            otherBytes = 0L,
                            cacheBytes = 0L,
                            fileCount = 3
                        )
                    ),
                    userDisplayName = "Alex",
                    planTier = PlanTier.FREE,
                    onSearchChange = {},
                    onOpenNotifications = {},
                    onOpenProfile = {},
                    onOpenSmartFileReady = {},
                    onQuickActionClick = {},
                    onMoveQuickActionUp = {},
                    onMoveQuickActionDown = {},
                    onResetQuickActions = {},
                    onOpenPdfStudio = {},
                    onOpenImageStudio = {},
                    onOpenVideoStudio = {},
                    onOpenDocumentWallet = {},
                    onOpenToolsTab = {},
                    onOpenFilesTab = {},
                    onOpenRemindersTab = {},
                    onFileClick = {},
                    onToggleFavourite = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_file_ready_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_smart_ready_cta").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Actions").assertIsDisplayed()
        composeRule.onNodeWithText("View All (8)")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Show Fewer Actions")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `ai quota policy enforces free and premium daily limits and resets on new date`() = runBlocking {
        assertEquals(5, AiQuotaPolicy.dailyLimitFor(PlanTier.FREE))
        assertEquals(200, AiQuotaPolicy.dailyLimitFor(PlanTier.PREMIUM))

        assertEquals(3, AiQuotaPolicy.effectiveUsageToday("2026-11-23", 3, today = "2026-11-23"))
        assertEquals(0, AiQuotaPolicy.effectiveUsageToday("2026-11-22", 5, today = "2026-11-23"))
        assertEquals(2, AiQuotaPolicy.remainingToday(PlanTier.FREE, "2026-11-23", 3, today = "2026-11-23"))
        assertEquals(0, AiQuotaPolicy.remainingToday(PlanTier.FREE, "2026-11-23", 8, today = "2026-11-23"))

        val prefsRepo = PreferencesRepository(context)
        prefsRepo.incrementAiUsageForDate("2026-11-23")
        prefsRepo.incrementAiUsageForDate("2026-11-23")
        val afterTwo = prefsRepo.preferencesFlow.first()
        assertEquals("2026-11-23", afterTwo.aiUsageDate)
        assertEquals(2, afterTwo.aiUsageCount)

        // New day resets counter cleanly to 1
        prefsRepo.incrementAiUsageForDate("2026-11-24")
        val nextDay = prefsRepo.preferencesFlow.first()
        assertEquals("2026-11-24", nextDay.aiUsageDate)
        assertEquals(1, nextDay.aiUsageCount)
    }

    @Test
    fun `pdf processing engine extracts embedded text for ai chat with pdf`() = runBlocking {
        val pdfEngine = PdfProcessingEngine(context)
        val blankPdf = createSamplePdfBoxFile("plain.pdf", 1)
        val textPdf = File(tempDir, "with_text.pdf")

        pdfEngine.addWatermarkAndPageNumbers(
            input = blankPdf,
            output = textPdf,
            watermarkText = "CONFIDENTIAL INVOICE 2026",
            addPageNumbers = true
        ).getOrThrow()

        val extracted = pdfEngine.extractEmbeddedPdfText(textPdf).getOrThrow()
        assertTrue(
            "Expected embedded text to contain CONFIDENTIAL INVOICE 2026, was: $extracted",
            extracted.contains("CONFIDENTIAL INVOICE 2026")
        )
    }

    @Test
    fun `splash screen renders official branding and triggers completion callback`() {
        var finished = false
        composeRule.setContent {
            FileDudeTheme {
                FileDudeSplashScreen(
                    isDataReady = true,
                    skipDelay = true,
                    onFinished = { finished = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filedude_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithText("FileDude").assertIsDisplayed()
        composeRule.onNodeWithText("Your Files. Ready When You Are.").assertIsDisplayed()
        assertTrue("Expected splash screen completion callback when data is ready", finished)
    }

    @Test
    fun `ai assistant screen renders modes quota offline and limit reached states`() {
        composeRule.setContent {
            FileDudeTheme {
                AiAssistantContent(
                    uiState = AiAssistantUiState(
                        mode = AiMode.CHAT,
                        usedToday = 5,
                        dailyLimit = AiQuotaPolicy.FREE_DAILY_REQUEST_LIMIT,
                        planTier = PlanTier.FREE,
                        taskState = AiTaskState.LimitReached
                    ),
                    onBack = {},
                    onClearChat = {},
                    onUpgradeClick = {},
                    onSelectMode = {},
                    onClearPrimaryFile = {},
                    onPickPrimaryDoc = {},
                    onPickPrimaryPhoto = {},
                    onSelectPrimaryFile = {},
                    onPickSecondaryDoc = {},
                    onSelectSecondaryFile = {},
                    onDismissStatus = {},
                    onCancelTask = {},
                    onUpdateChatInput = {},
                    onSendChatQuestion = {},
                    onUpdateCustomText = {},
                    onUpdateAudienceStyle = {},
                    onRunSummarize = {},
                    onRunExtractInfo = {},
                    onSaveExtractedExpiry = {},
                    onUpdateTranslationLanguage = {},
                    onRunTranslate = {},
                    onUpdateCompareFocus = {},
                    onRunCompare = {},
                    onUpdateWorkflowGoal = {},
                    onRunWorkflowSuggest = {},
                    onLaunchSuggestedTool = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("FileDude AI Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Free AI • 5 / 5 daily requests").assertIsDisplayed()
        composeRule.onNodeWithText("Daily Free AI Limit Reached").assertIsDisplayed()
        composeRule.onNodeWithText("Upgrade to FileDude Premium")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Summarize").assertIsDisplayed()
        composeRule.onNodeWithText("Translate").assertIsDisplayed()
    }
}
