package com.example.features.ai

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiChatMessage
import com.example.core.ai.AiExtractionResult
import com.example.core.ai.AiQuotaPolicy
import com.example.core.ai.AiSummaryResult
import com.example.core.ai.AiWorkflowSuggestionResult
import com.example.core.ai.FileDudeAiRepository
import com.example.core.ai.PreparedDocumentPayload
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.PreferencesRepository
import com.example.core.model.PlanTier
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import com.example.core.model.WorkspaceFile
import com.example.reminders.DocumentWalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "AiAssistantVM"

enum class AiMode(val label: String) {
    CHAT("Chat & Ask PDF"),
    SUMMARIZE("Summarize"),
    EXTRACT("Extract Info"),
    TRANSLATE("Translate"),
    COMPARE("Compare"),
    WORKFLOW("Workflow Advice");

    companion object {
        fun fromRaw(raw: String): AiMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CHAT
    }
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val partialText: String) : AiTaskState
    data class Offline(val message: String) : AiTaskState
    data object LimitReached : AiTaskState
    data class Error(val message: String) : AiTaskState
    data object Success : AiTaskState
}

data class AiAssistantUiState(
    val mode: AiMode = AiMode.CHAT,
    val primaryFile: WorkspaceFile? = null,
    val secondaryFile: WorkspaceFile? = null,
    val primaryPayload: PreparedDocumentPayload? = null,
    val secondaryPayload: PreparedDocumentPayload? = null,
    val isPreparingFile: Boolean = false,
    val workspaceFiles: List<WorkspaceFile> = emptyList(),
    val chatMessages: List<AiChatMessage> = emptyList(),
    val chatInput: String = "",
    val customTextInput: String = "",
    val audienceStyle: String = "Clear & Concise",
    val summaryResult: AiSummaryResult? = null,
    val extractionResult: AiExtractionResult? = null,
    val translationLanguage: String = "Spanish",
    val translationOutput: String = "",
    val compareFocusArea: String = "",
    val compareOutput: String = "",
    val workflowGoal: String = "",
    val workflowResult: AiWorkflowSuggestionResult? = null,
    val taskState: AiTaskState = AiTaskState.Idle,
    val usedToday: Int = 0,
    val dailyLimit: Int = AiQuotaPolicy.FREE_DAILY_REQUEST_LIMIT,
    val planTier: PlanTier = PlanTier.FREE,
    val statusMessage: String? = null
)

class AiAssistantViewModel(
    private val aiRepository: FileDudeAiRepository,
    private val fileRepository: WorkspaceFileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val walletRepository: DocumentWalletRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            fileRepository.activeFiles.collect { files ->
                _uiState.update { it.copy(workspaceFiles = files) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                val used = AiQuotaPolicy.effectiveUsageToday(prefs.aiUsageDate, prefs.aiUsageCount)
                _uiState.update { state ->
                    state.copy(
                        usedToday = used,
                        dailyLimit = AiQuotaPolicy.dailyLimitFor(state.planTier)
                    )
                }
            }
        }
    }

    fun updatePlanTier(tier: PlanTier) {
        _uiState.update {
            it.copy(
                planTier = tier,
                dailyLimit = AiQuotaPolicy.dailyLimitFor(tier),
                taskState = if (it.taskState is AiTaskState.LimitReached && tier == PlanTier.PREMIUM) AiTaskState.Idle else it.taskState
            )
        }
    }

    fun setInitialArgs(modeRaw: String, fileId: String?) {
        val targetMode = AiMode.fromRaw(modeRaw)
        _uiState.update { it.copy(mode = targetMode) }
        if (!fileId.isNullOrBlank()) {
            viewModelScope.launch {
                val files = fileRepository.activeFiles.first()
                files.firstOrNull { it.id == fileId }?.let { selectPrimaryFile(it) }
            }
        }
    }

    fun selectMode(mode: AiMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                taskState = AiTaskState.Idle,
                statusMessage = null
            )
        }
    }

    fun updateChatInput(value: String) {
        _uiState.update { it.copy(chatInput = value) }
    }

    fun updateCustomText(value: String) {
        _uiState.update { it.copy(customTextInput = value) }
    }

    fun updateAudienceStyle(style: String) {
        _uiState.update { it.copy(audienceStyle = style) }
    }

    fun updateTranslationLanguage(language: String) {
        _uiState.update { it.copy(translationLanguage = language) }
    }

    fun updateCompareFocus(value: String) {
        _uiState.update { it.copy(compareFocusArea = value) }
    }

    fun updateWorkflowGoal(value: String) {
        _uiState.update { it.copy(workflowGoal = value) }
    }

    fun importPrimaryUri(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, "AI Inputs")
                .onSuccess { selectPrimaryFile(it) }
                .onFailure { e -> _uiState.update { it.copy(taskState = AiTaskState.Error(e.localizedMessage ?: "Import failed")) } }
        }
    }

    fun importSecondaryUri(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, "AI Inputs")
                .onSuccess { selectSecondaryFile(it) }
                .onFailure { e -> _uiState.update { it.copy(taskState = AiTaskState.Error(e.localizedMessage ?: "Import failed")) } }
        }
    }

    fun selectPrimaryFile(file: WorkspaceFile) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    primaryFile = file,
                    isPreparingFile = true,
                    summaryResult = null,
                    extractionResult = null,
                    workflowResult = null,
                    statusMessage = null
                )
            }
            val payload = aiRepository.prepareDocumentPayload(file).getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isPreparingFile = false,
                        taskState = AiTaskState.Error(e.localizedMessage ?: "Could not prepare document.")
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    primaryPayload = payload,
                    isPreparingFile = false
                )
            }
        }
    }

    fun selectSecondaryFile(file: WorkspaceFile) {
        viewModelScope.launch {
            _uiState.update { it.copy(secondaryFile = file, isPreparingFile = true) }
            val payload = aiRepository.prepareDocumentPayload(file).getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isPreparingFile = false,
                        taskState = AiTaskState.Error(e.localizedMessage ?: "Could not prepare comparison file.")
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(secondaryPayload = payload, isPreparingFile = false) }
        }
    }

    fun clearPrimaryFile() {
        _uiState.update { it.copy(primaryFile = null, primaryPayload = null) }
    }

    fun clearChat() {
        _uiState.update { it.copy(chatMessages = emptyList(), taskState = AiTaskState.Idle) }
    }

    fun sendChatQuestion(overrideQuestion: String? = null) {
        val question = (overrideQuestion ?: _uiState.value.chatInput).trim()
        if (question.isEmpty()) return

        launchGuardedAiTask {
            val state = _uiState.value
            val userMsg = AiChatMessage(isUser = true, text = question)
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + userMsg,
                    chatInput = if (overrideQuestion == null) "" else it.chatInput,
                    taskState = AiTaskState.Loading
                )
            }
            var finalAnswer = ""
            aiRepository.streamChatMessage(
                history = state.chatMessages,
                userQuestion = question,
                attachedDocument = state.primaryFile,
                payload = state.primaryPayload
            ).collect { partial ->
                finalAnswer = partial
                _uiState.update { it.copy(taskState = AiTaskState.Streaming(partial)) }
            }
            val botMsg = AiChatMessage(isUser = false, text = finalAnswer)
            recordSuccessfulAiRequest()
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + botMsg,
                    taskState = AiTaskState.Success
                )
            }
        }
    }

    fun runSummarize() {
        val state = _uiState.value
        if (state.primaryFile == null && state.customTextInput.isBlank()) {
            _uiState.update { it.copy(taskState = AiTaskState.Error("Select a workspace/device file or paste text to summarize.")) }
            return
        }
        launchGuardedAiTask {
            _uiState.update { it.copy(taskState = AiTaskState.Loading) }
            val result = aiRepository.summarizeAndExplain(
                file = state.primaryFile,
                pastedText = state.customTextInput,
                payload = state.primaryPayload,
                audienceStyle = state.audienceStyle
            ).getOrThrow()
            recordSuccessfulAiRequest()
            _uiState.update { it.copy(summaryResult = result, taskState = AiTaskState.Success) }
        }
    }

    fun runExtractInfo() {
        val state = _uiState.value
        if (state.primaryFile == null && state.customTextInput.isBlank()) {
            _uiState.update { it.copy(taskState = AiTaskState.Error("Select a file or paste text to extract information.")) }
            return
        }
        launchGuardedAiTask {
            _uiState.update { it.copy(taskState = AiTaskState.Loading) }
            val result = aiRepository.extractStructuredInfo(
                file = state.primaryFile,
                pastedText = state.customTextInput,
                payload = state.primaryPayload
            ).getOrThrow()
            recordSuccessfulAiRequest()
            _uiState.update { it.copy(extractionResult = result, taskState = AiTaskState.Success) }
        }
    }

    fun saveExtractedExpiryToWallet(extraction: AiExtractionResult) {
        viewModelScope.launch {
            val iso = extraction.detectedExpiryIso.trim()
            val expiryMs = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)?.time
            }.getOrNull()
            if (expiryMs == null) {
                _uiState.update { it.copy(statusMessage = "Expiry date '$iso' could not be parsed cleanly. Add it manually in Reminders.") }
                return@launch
            }
            val category = WalletCategory.entries.firstOrNull {
                extraction.documentType.contains(it.name.replace("_", " "), ignoreCase = true) ||
                    extraction.documentType.contains(it.label, ignoreCase = true)
            } ?: WalletCategory.OTHER
            val doc = WalletDocument(
                title = extraction.detectedTitle.ifBlank { extraction.documentType },
                category = category,
                attachmentPath = _uiState.value.primaryFile?.localPath,
                attachmentName = _uiState.value.primaryFile?.displayName,
                notes = extraction.fields.take(5).joinToString(" • ") { "${it.label}: ${it.value}" },
                expiryDateMillis = expiryMs
            )
            walletRepository.saveDocument(doc, syncToCloud = false)
            _uiState.update { it.copy(statusMessage = "Saved '${doc.title}' to Document Wallet (Reminders).") }
        }
    }

    fun runTranslate() {
        val state = _uiState.value
        if (state.primaryFile == null && state.customTextInput.isBlank()) {
            _uiState.update { it.copy(taskState = AiTaskState.Error("Select a file or paste text to translate.")) }
            return
        }
        launchGuardedAiTask {
            _uiState.update { it.copy(taskState = AiTaskState.Loading, translationOutput = "") }
            var finalOutput = ""
            aiRepository.streamTranslate(
                file = state.primaryFile,
                pastedText = state.customTextInput,
                payload = state.primaryPayload,
                targetLanguage = state.translationLanguage
            ).collect { partial ->
                finalOutput = partial
                _uiState.update {
                    it.copy(
                        translationOutput = partial,
                        taskState = AiTaskState.Streaming(partial)
                    )
                }
            }
            recordSuccessfulAiRequest()
            _uiState.update { it.copy(translationOutput = finalOutput, taskState = AiTaskState.Success) }
        }
    }

    fun runCompare() {
        val state = _uiState.value
        val fileA = state.primaryFile
        val payloadA = state.primaryPayload
        val fileB = state.secondaryFile
        val payloadB = state.secondaryPayload
        if (fileA == null || payloadA == null || fileB == null || payloadB == null) {
            _uiState.update { it.copy(taskState = AiTaskState.Error("Pick both Document A and Document B to compare.")) }
            return
        }
        launchGuardedAiTask {
            _uiState.update { it.copy(taskState = AiTaskState.Loading, compareOutput = "") }
            var finalOutput = ""
            aiRepository.streamCompareDocuments(
                fileA = fileA,
                payloadA = payloadA,
                fileB = fileB,
                payloadB = payloadB,
                focusArea = state.compareFocusArea
            ).collect { partial ->
                finalOutput = partial
                _uiState.update {
                    it.copy(
                        compareOutput = partial,
                        taskState = AiTaskState.Streaming(partial)
                    )
                }
            }
            recordSuccessfulAiRequest()
            _uiState.update { it.copy(compareOutput = finalOutput, taskState = AiTaskState.Success) }
        }
    }

    fun runWorkflowSuggest() {
        val state = _uiState.value
        val file = state.primaryFile
        if (file == null) {
            _uiState.update { it.copy(taskState = AiTaskState.Error("Pick a file first so AI can suggest the best FileDude workflow.")) }
            return
        }
        launchGuardedAiTask {
            _uiState.update { it.copy(taskState = AiTaskState.Loading) }
            val result = aiRepository.suggestWorkflows(
                file = file,
                userGoal = state.workflowGoal,
                payload = state.primaryPayload
            ).getOrThrow()
            recordSuccessfulAiRequest()
            _uiState.update { it.copy(workflowResult = result, taskState = AiTaskState.Success) }
        }
    }

    fun cancelActiveTask() {
        activeJob?.cancel()
        _uiState.update { it.copy(taskState = AiTaskState.Idle) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null, taskState = AiTaskState.Idle) }
    }

    private fun launchGuardedAiTask(block: suspend () -> Unit) {
        val state = _uiState.value
        if (!aiRepository.isOnline()) {
            _uiState.update {
                it.copy(
                    taskState = AiTaskState.Offline(
                        "You are currently offline. Connect to Wi-Fi or mobile data to use FileDude AI Assistant—all PDF, Image, and Video tools still work offline."
                    )
                )
            }
            return
        }
        if (state.usedToday >= state.dailyLimit) {
            _uiState.update { it.copy(taskState = AiTaskState.LimitReached) }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                block()
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _uiState.update {
                    it.copy(taskState = AiTaskState.Error("Request timed out after 60 seconds. Please try again."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI execution error", e)
                _uiState.update {
                    it.copy(taskState = AiTaskState.Error(e.localizedMessage ?: "AI request failed. Check connection or Firebase App Check configuration."))
                }
            }
        }
    }

    private suspend fun recordSuccessfulAiRequest() {
        preferencesRepository.incrementAiUsageForDate(AiQuotaPolicy.todayDateKey())
    }
}
