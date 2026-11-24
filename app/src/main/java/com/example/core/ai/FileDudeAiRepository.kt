package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.core.engines.PdfProcessingEngine
import com.example.core.model.FileCategory
import com.example.core.model.PlanTier
import com.example.core.model.WorkspaceFile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FileDudeAiRepo"
private const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

object AiQuotaPolicy {
    const val FREE_DAILY_REQUEST_LIMIT = 5
    const val PREMIUM_DAILY_REQUEST_LIMIT = 200

    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun dailyLimitFor(planTier: PlanTier): Int = when (planTier) {
        PlanTier.FREE -> FREE_DAILY_REQUEST_LIMIT
        PlanTier.PREMIUM -> PREMIUM_DAILY_REQUEST_LIMIT
    }

    fun effectiveUsageToday(storedDate: String, storedCount: Int, today: String = todayDateKey()): Int {
        return if (storedDate == today) storedCount.coerceAtLeast(0) else 0
    }

    fun remainingToday(planTier: PlanTier, storedDate: String, storedCount: Int, today: String = todayDateKey()): Int {
        val used = effectiveUsageToday(storedDate, storedCount, today)
        return (dailyLimitFor(planTier) - used).coerceAtLeast(0)
    }
}

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class AiSummaryResult(
    val title: String,
    val executiveSummary: String,
    val simpleExplanation: String,
    val keyPoints: List<String>,
    val actionItems: List<String>
)

data class AiExtractedField(
    val label: String,
    val value: String
)

data class AiExtractionResult(
    val documentType: String,
    val detectedTitle: String,
    val detectedExpiryIso: String,
    val fields: List<AiExtractedField>,
    val summaryNote: String
)

enum class AiToolRouteKey(val label: String) {
    SMART_FILE_READY("Smart File Ready"),
    PDF_COMPRESS("Compress PDF"),
    PDF_OCR("OCR PDF"),
    PDF_TO_IMAGES("PDF to JPG/PNG"),
    PDF_SECURITY("Protect / Unlock PDF"),
    IMAGE_COMPRESS("Target KB Compress"),
    IMAGE_RESIZE("Resize / Passport"),
    IMAGE_EXIF("Strip Camera EXIF"),
    IMAGE_TO_PDF("Image to PDF"),
    VIDEO_COMPRESS("Compress Video");

    companion object {
        fun fromRaw(raw: String): AiToolRouteKey =
            entries.firstOrNull { it.name == raw.trim().uppercase() } ?: SMART_FILE_READY
    }
}

data class AiWorkflowStep(
    val toolKey: AiToolRouteKey,
    val reason: String,
    val priority: String
)

data class AiWorkflowSuggestionResult(
    val assessment: String,
    val steps: List<AiWorkflowStep>
)

data class PreparedDocumentPayload(
    val summaryHeader: String,
    val extractedText: String,
    val previewBitmaps: List<Bitmap>
)

class FileDudeAiRepository(
    private val context: Context,
    private val pdfEngine: PdfProcessingEngine
) {
    private val chatAndStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            systemInstruction = content {
                text(
                    "You are FileDude AI Assistant inside the FileDude Android app. " +
                        "Help users understand PDFs, documents, images, and forms clearly, accurately, and concisely. " +
                        "Never fabricate document values that are not present in the provided text or images. " +
                        "If text is unreadable or missing, state that honestly."
                )
            }
        )
    }

    private val summaryJsonModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short descriptive document title"),
                "executiveSummary" to Schema.string(description = "Concise 2-4 sentence factual summary"),
                "simpleExplanation" to Schema.string(description = "Plain-language explanation easy for any reader"),
                "keyPoints" to Schema.array(
                    items = Schema.string(),
                    description = "Up to 6 bullet takeaways from the content"
                ),
                "actionItems" to Schema.array(
                    items = Schema.string(),
                    description = "Deadlines, forms, signatures, or next actions implied by the document"
                )
            ),
            optionalProperties = listOf("actionItems")
        )
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val extractionJsonModel by lazy {
        val fieldSchema = Schema.obj(
            properties = mapOf(
                "label" to Schema.string(description = "Field label such as Name, Document Number, Total, Issuer, Date"),
                "value" to Schema.string(description = "Extracted factual value from the document")
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "documentType" to Schema.string(description = "Detected type e.g. Invoice, Passport, Contract, Receipt, Certificate, ID"),
                "detectedTitle" to Schema.string(description = "Suggested title if saved to Document Wallet"),
                "detectedExpiryIso" to Schema.string(
                    description = "Expiry or due date in YYYY-MM-DD format if clearly stated in the document, otherwise empty string",
                    nullable = true
                ),
                "fields" to Schema.array(
                    items = fieldSchema,
                    description = "Important structured entities, dates, IDs, amounts, emails, addresses found"
                ),
                "summaryNote" to Schema.string(description = "One-sentence summary of extracted data or omissions")
            ),
            optionalProperties = listOf("detectedExpiryIso")
        )
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val workflowJsonModel by lazy {
        val stepSchema = Schema.obj(
            properties = mapOf(
                "toolKey" to Schema.enumeration(
                    values = AiToolRouteKey.entries.map { it.name },
                    description = "FileDude tool action identifier"
                ),
                "reason" to Schema.string(description = "Why this FileDude tool helps the user's goal"),
                "priority" to Schema.enumeration(
                    values = listOf("HIGH", "MEDIUM", "OPTIONAL"),
                    description = "Recommended priority"
                )
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "assessment" to Schema.string(description = "Assessment of file readiness and optimization potential"),
                "steps" to Schema.array(
                    items = stepSchema,
                    description = "Ordered list of 1 to 4 recommended FileDude actions"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun isOnline(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrElse {
            // Fallback to general active network check if validation flag lags on some emulators
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    suspend fun prepareDocumentPayload(workspaceFile: WorkspaceFile): Result<PreparedDocumentPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val diskFile = File(workspaceFile.localPath)
                require(diskFile.exists()) { "Selected file is not available in workspace storage." }

                val header = buildString {
                    append("File: ${workspaceFile.displayName}\n")
                    append("Category: ${workspaceFile.category.label}, MIME: ${workspaceFile.mimeType}, Size: ${workspaceFile.sizeBytes} bytes")
                    if (workspaceFile.widthPx > 0) append(", Dimensions: ${workspaceFile.widthPx}x${workspaceFile.heightPx}px")
                    if (workspaceFile.pageCount > 0) append(", Pages: ${workspaceFile.pageCount}")
                    if (workspaceFile.durationMs > 0) append(", Duration: ${workspaceFile.durationMs}ms")
                }

                when (workspaceFile.category) {
                    FileCategory.PDF -> {
                        val extracted = pdfEngine.extractEmbeddedPdfText(diskFile).getOrDefault("")
                        // Include visual page renders if text is short (scanned/image PDF) or first 2 pages for layout understanding
                        val maxPagesToRender = if (extracted.length < 150) 3 else 1
                        val bitmaps = pdfEngine.renderPagesForAi(diskFile, maxPages = maxPagesToRender, targetWidthPx = 900)
                            .getOrDefault(emptyList())
                        PreparedDocumentPayload(header, extracted, bitmaps)
                    }
                    FileCategory.IMAGE -> {
                        val bmp = decodeScaledBitmap(diskFile, maxDimension = 1200)
                        PreparedDocumentPayload(header, "", listOfNotNull(bmp))
                    }
                    FileCategory.DOCUMENT -> {
                        val text = runCatching {
                            if (diskFile.length() <= 1024L * 1024L) diskFile.readText().take(40_000) else ""
                        }.getOrDefault("")
                        PreparedDocumentPayload(header, text, emptyList())
                    }
                    else -> PreparedDocumentPayload(header, "", emptyList())
                }
            }
        }

    fun streamChatMessage(
        history: List<AiChatMessage>,
        userQuestion: String,
        attachedDocument: WorkspaceFile?,
        payload: PreparedDocumentPayload?
    ): Flow<String> = flow {
        val contentHistory: List<Content> = history.takeLast(10).map { msg ->
            content(role = if (msg.isUser) "user" else "model") {
                text(msg.text)
            }
        }

        val promptContent = buildContentForTask(
            instruction = userQuestion,
            attachedFile = attachedDocument,
            payload = payload
        )

        val chat = chatAndStreamModel.startChat(history = contentHistory)
        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(promptContent).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("The AI model returned an empty response. Try rephrasing your question.")
        }
    }

    suspend fun summarizeAndExplain(
        file: WorkspaceFile?,
        pastedText: String,
        payload: PreparedDocumentPayload?,
        audienceStyle: String
    ): Result<AiSummaryResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Analyze the provided file/text and produce a structured summary & explanation.")
                appendLine("Explanation style: $audienceStyle.")
                if (pastedText.isNotBlank()) {
                    appendLine("\nAdditional user text:\n${pastedText.take(25_000)}")
                }
            }
            val inputContent = buildContentForTask(prompt, file, payload)
            val response = withTimeout(AI_TIMEOUT_MS) {
                summaryJsonModel.generateContent(inputContent)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Model returned empty summary JSON.")
            val json = JSONObject(jsonStr)
            Result.success(
                AiSummaryResult(
                    title = json.optString("title", file?.displayName ?: "Document Summary"),
                    executiveSummary = json.optString("executiveSummary", ""),
                    simpleExplanation = json.optString("simpleExplanation", ""),
                    keyPoints = json.optJsonStringList("keyPoints"),
                    actionItems = json.optJsonStringList("actionItems")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI summary timed out", e)
            Result.failure(Exception("Summary request timed out after 60 seconds. Please try a shorter excerpt."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI summary failed."))
        }
    }

    suspend fun extractStructuredInfo(
        file: WorkspaceFile?,
        pastedText: String,
        payload: PreparedDocumentPayload?
    ): Result<AiExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Extract key structured information from this document: dates, expiry date (if present as YYYY-MM-DD), ID/reference numbers, names, amounts, issuer, and key terms.")
                if (pastedText.isNotBlank()) {
                    appendLine("\nText input:\n${pastedText.take(25_000)}")
                }
            }
            val inputContent = buildContentForTask(prompt, file, payload)
            val response = withTimeout(AI_TIMEOUT_MS) {
                extractionJsonModel.generateContent(inputContent)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Model returned empty extraction JSON.")
            val json = JSONObject(jsonStr)
            val fieldsArr = json.optJSONArray("fields")
            val parsedFields = buildList {
                if (fieldsArr != null) {
                    for (i in 0 until fieldsArr.length()) {
                        val item = fieldsArr.optJSONObject(i) ?: continue
                        val lbl = item.optString("label").trim()
                        val valStr = item.optString("value").trim()
                        if (lbl.isNotEmpty() && valStr.isNotEmpty()) {
                            add(AiExtractedField(lbl, valStr))
                        }
                    }
                }
            }
            Result.success(
                AiExtractionResult(
                    documentType = json.optString("documentType", "Document"),
                    detectedTitle = json.optString("detectedTitle", file?.displayName ?: "Extracted Document"),
                    detectedExpiryIso = json.optString("detectedExpiryIso", "").takeIf { it != "null" }.orEmpty(),
                    fields = parsedFields,
                    summaryNote = json.optString("summaryNote", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI extraction timed out", e)
            Result.failure(Exception("Information extraction timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI extraction failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI information extraction failed."))
        }
    }

    fun streamTranslate(
        file: WorkspaceFile?,
        pastedText: String,
        payload: PreparedDocumentPayload?,
        targetLanguage: String
    ): Flow<String> = flow {
        val prompt = buildString {
            appendLine("Translate the complete readable content of the provided document/text faithfully into $targetLanguage.")
            appendLine("Preserve headings, bullet structure, numbers, and dates accurately.")
            if (pastedText.isNotBlank()) {
                appendLine("\nText to translate:\n${pastedText.take(25_000)}")
            }
        }
        val inputContent = buildContentForTask(prompt, file, payload)
        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chatAndStreamModel.generateContentStream(inputContent).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Translation returned an empty response.")
        }
    }

    fun streamCompareDocuments(
        fileA: WorkspaceFile,
        payloadA: PreparedDocumentPayload,
        fileB: WorkspaceFile,
        payloadB: PreparedDocumentPayload,
        focusArea: String
    ): Flow<String> = flow {
        val combinedContent = content {
            text(
                "Compare Document A and Document B thoroughly. Highlight key differences, changed numbers/dates, added or removed clauses, and practical implications.\n" +
                    (if (focusArea.isNotBlank()) "Specific focus requested by user: $focusArea\n" else "") +
                    "\n=== DOCUMENT A ===\n${payloadA.summaryHeader}\n${payloadA.extractedText.take(18_000)}"
            )
            payloadA.previewBitmaps.take(2).forEach { bmp -> image(bmp) }
            text(
                "\n=== DOCUMENT B ===\n${payloadB.summaryHeader}\n${payloadB.extractedText.take(18_000)}"
            )
            payloadB.previewBitmaps.take(2).forEach { bmp -> image(bmp) }
        }
        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chatAndStreamModel.generateContentStream(combinedContent).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Document comparison returned an empty response.")
        }
    }

    suspend fun suggestWorkflows(
        file: WorkspaceFile,
        userGoal: String,
        payload: PreparedDocumentPayload?
    ): Result<AiWorkflowSuggestionResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Recommend the best FileDude editing and verification workflow for this file based on its metadata and content.")
                appendLine("User's stated goal: ${userGoal.ifBlank { "Prepare cleanly for sharing, submission, or archiving" }}.")
                appendLine("Choose only from the schema toolKey enum values and explain clearly why each step helps.")
            }
            val inputContent = buildContentForTask(prompt, file, payload)
            val response = withTimeout(AI_TIMEOUT_MS) {
                workflowJsonModel.generateContent(inputContent)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Model returned empty workflow JSON.")
            val json = JSONObject(jsonStr)
            val stepsArr = json.optJSONArray("steps")
            val parsedSteps = buildList {
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val obj = stepsArr.optJSONObject(i) ?: continue
                        add(
                            AiWorkflowStep(
                                toolKey = AiToolRouteKey.fromRaw(obj.optString("toolKey")),
                                reason = obj.optString("reason", ""),
                                priority = obj.optString("priority", "HIGH")
                            )
                        )
                    }
                }
            }
            Result.success(
                AiWorkflowSuggestionResult(
                    assessment = json.optString("assessment", ""),
                    steps = parsedSteps
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI workflow suggestion timed out", e)
            Result.failure(Exception("Workflow suggestion timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI workflow suggestion failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate workflow suggestions."))
        }
    }

    private fun buildContentForTask(
        instruction: String,
        attachedFile: WorkspaceFile?,
        payload: PreparedDocumentPayload?
    ): Content = content {
        val contextBlock = buildString {
            appendLine(instruction)
            if (payload != null) {
                appendLine("\n--- ATTACHED FILE CONTEXT ---")
                appendLine(payload.summaryHeader)
                if (payload.extractedText.isNotBlank()) {
                    appendLine("\nExtracted Text Content:\n${payload.extractedText}")
                }
            } else if (attachedFile != null) {
                appendLine("\n--- ATTACHED FILE METADATA ---")
                appendLine("${attachedFile.displayName} (${attachedFile.category.label}, ${attachedFile.sizeBytes} bytes)")
            }
        }
        text(contextBlock)
        payload?.previewBitmaps?.take(3)?.forEach { bmp ->
            image(bmp)
        }
    }

    private fun decodeScaledBitmap(file: File, maxDimension: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxDimension * 2 || opts.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / largest.toFloat()
        val resized = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (resized !== decoded) decoded.recycle()
        return resized
    }

    private fun JSONObject.optJsonStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}
