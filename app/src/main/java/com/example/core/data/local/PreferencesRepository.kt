package com.example.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppThemeMode
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.QuickActionId
import com.example.core.model.VideoExportPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fileDudeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "filedude_preferences"
)

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val onboardingCompleted: Boolean = false,
    val isGuestMode: Boolean = false,
    val quickActionsOrder: List<QuickActionId> = QuickActionId.entries.toList(),
    val expiryNotificationsEnabled: Boolean = true,
    val defaultImageFormat: ImageOutputFormat = ImageOutputFormat.JPG,
    val defaultImageQuality: Int = 88,
    val defaultPdfPageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val defaultVideoPreset: VideoExportPreset = VideoExportPreset.P720,
    val preserveOriginalByDefault: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val analyticsConsentGranted: Boolean = false,
    val aiUsageDate: String = "",
    val aiUsageCount: Int = 0
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val GUEST_MODE = booleanPreferencesKey("guest_mode")
        val QUICK_ACTIONS_CSV = stringPreferencesKey("quick_actions_csv")
        val EXPIRY_NOTIFS = booleanPreferencesKey("expiry_notifications_enabled")
        val DEFAULT_IMAGE_FORMAT = stringPreferencesKey("default_image_format")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val DEFAULT_PDF_PAGE_SIZE = stringPreferencesKey("default_pdf_page_size")
        val DEFAULT_VIDEO_PRESET = stringPreferencesKey("default_video_preset")
        val PRESERVE_ORIGINAL = booleanPreferencesKey("preserve_original")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
        val AI_USAGE_DATE = stringPreferencesKey("ai_usage_date")
        val AI_USAGE_COUNT = intPreferencesKey("ai_usage_count")
    }

    val preferencesFlow: Flow<UserPreferences> = context.fileDudeDataStore.data.map { prefs ->
        val theme = AppThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] }
            ?: AppThemeMode.LIGHT
        val quickActions = prefs[Keys.QUICK_ACTIONS_CSV]
            ?.split(",")
            ?.mapNotNull { QuickActionId.fromRaw(it.trim()) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: QuickActionId.entries.toList()
        val imgFmt = ImageOutputFormat.entries.firstOrNull { it.name == prefs[Keys.DEFAULT_IMAGE_FORMAT] }
            ?: ImageOutputFormat.JPG
        val pdfSize = PdfPageSizeOption.entries.firstOrNull { it.name == prefs[Keys.DEFAULT_PDF_PAGE_SIZE] }
            ?: PdfPageSizeOption.A4
        val videoPreset = VideoExportPreset.entries.firstOrNull { it.name == prefs[Keys.DEFAULT_VIDEO_PRESET] }
            ?: VideoExportPreset.P720

        UserPreferences(
            themeMode = theme,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            isGuestMode = prefs[Keys.GUEST_MODE] ?: false,
            quickActionsOrder = quickActions,
            expiryNotificationsEnabled = prefs[Keys.EXPIRY_NOTIFS] ?: true,
            defaultImageFormat = imgFmt,
            defaultImageQuality = (prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 88).coerceIn(10, 100),
            defaultPdfPageSize = pdfSize,
            defaultVideoPreset = videoPreset,
            preserveOriginalByDefault = prefs[Keys.PRESERVE_ORIGINAL] ?: true,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false,
            analyticsConsentGranted = prefs[Keys.ANALYTICS_CONSENT] ?: false,
            aiUsageDate = prefs[Keys.AI_USAGE_DATE].orEmpty(),
            aiUsageCount = prefs[Keys.AI_USAGE_COUNT] ?: 0
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.fileDudeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean, guestMode: Boolean) {
        context.fileDudeDataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = completed
            it[Keys.GUEST_MODE] = guestMode
        }
    }

    suspend fun setGuestMode(guestMode: Boolean) {
        context.fileDudeDataStore.edit { it[Keys.GUEST_MODE] = guestMode }
    }

    suspend fun setQuickActionsOrder(actions: List<QuickActionId>) {
        context.fileDudeDataStore.edit {
            it[Keys.QUICK_ACTIONS_CSV] = actions.joinToString(",") { item -> item.name }
        }
    }

    suspend fun setExpiryNotificationsEnabled(enabled: Boolean) {
        context.fileDudeDataStore.edit { it[Keys.EXPIRY_NOTIFS] = enabled }
    }

    suspend fun setDefaultImageSettings(format: ImageOutputFormat, quality: Int) {
        context.fileDudeDataStore.edit {
            it[Keys.DEFAULT_IMAGE_FORMAT] = format.name
            it[Keys.DEFAULT_IMAGE_QUALITY] = quality.coerceIn(10, 100)
        }
    }

    suspend fun setDefaultPdfPageSize(pageSize: PdfPageSizeOption) {
        context.fileDudeDataStore.edit { it[Keys.DEFAULT_PDF_PAGE_SIZE] = pageSize.name }
    }

    suspend fun setDefaultVideoPreset(preset: VideoExportPreset) {
        context.fileDudeDataStore.edit { it[Keys.DEFAULT_VIDEO_PRESET] = preset.name }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.fileDudeDataStore.edit { it[Keys.CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setAnalyticsConsent(granted: Boolean) {
        context.fileDudeDataStore.edit { it[Keys.ANALYTICS_CONSENT] = granted }
    }

    suspend fun incrementAiUsageForDate(dateKey: String) {
        context.fileDudeDataStore.edit { prefs ->
            val currentDate = prefs[Keys.AI_USAGE_DATE].orEmpty()
            val prevCount = if (currentDate == dateKey) (prefs[Keys.AI_USAGE_COUNT] ?: 0) else 0
            prefs[Keys.AI_USAGE_DATE] = dateKey
            prefs[Keys.AI_USAGE_COUNT] = prevCount + 1
        }
    }
}
