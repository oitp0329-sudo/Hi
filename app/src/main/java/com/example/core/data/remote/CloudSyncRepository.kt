package com.example.core.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.PlanTier
import com.example.core.model.SmartPreset
import com.example.core.model.SmartTargetFormat
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @com.google.firebase.firestore.DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val planTier: String = "FREE",
    val cloudSyncEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// Use official Firestore annotations cleanly
@Keep
@IgnoreExtraProperties
data class CloudWalletDocument(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "OTHER",
    val notes: String? = null,
    val issueDateMillis: Long? = null,
    val expiryDateMillis: Long = 0L,
    val reminderOffsetsCsv: String = "90,30,7,1",
    val renewed: Boolean = false,
    val lastRenewedAtMillis: Long? = null,
    val renewalHistorySummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): WalletDocument = WalletDocument(
        id = id,
        title = title,
        category = WalletCategory.fromRaw(category),
        notes = notes.orEmpty(),
        issueDateMillis = issueDateMillis,
        expiryDateMillis = expiryDateMillis,
        reminderOffsetsDays = reminderOffsetsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(90, 30, 7, 1) },
        isRenewed = renewed,
        lastRenewedAtMillis = lastRenewedAtMillis,
        renewalHistory = renewalHistorySummary?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudSmartPreset(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetFormat: String = "JPG",
    val maxSizeBytes: Long = 200 * 1024L,
    val minWidthPx: Int = 0,
    val maxWidthPx: Int = 0,
    val minHeightPx: Int = 0,
    val maxHeightPx: Int = 0,
    val aspectRatio: String? = null,
    val pdfPageSize: String? = null,
    val qualityPercent: Int = 88,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): SmartPreset = SmartPreset(
        id = id,
        name = name,
        description = "Synced custom requirement preset",
        isBuiltIn = false,
        targetFormat = SmartTargetFormat.fromRaw(targetFormat),
        maxSizeBytes = maxSizeBytes,
        minWidthPx = minWidthPx,
        maxWidthPx = maxWidthPx,
        minHeightPx = minHeightPx,
        maxHeightPx = maxHeightPx,
        aspectRatioLabel = aspectRatio.orEmpty(),
        pdfPageSize = PdfPageSizeOption.fromRaw(pdfPageSize ?: "ORIGINAL"),
        qualityPercent = qualityPercent
    )
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
private annotation class DocumentIdAnnotationPlaceholder

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in with Google before accessing cloud sync.")
    }

    private fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(128).ifBlank { "item_${System.currentTimeMillis()}" }
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        planTier: PlanTier,
        cloudSyncEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "FileDude User" }.take(80)
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.take(160),
                    "planTier" to planTier.name,
                    "cloudSyncEnabled" to cloudSyncEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to safeName,
                    "email" to email?.take(160),
                    "planTier" to planTier.name,
                    "cloudSyncEnabled" to cloudSyncEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeCloudWalletDocuments(): Flow<List<WalletDocument>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/walletDocuments"
        emitAll(
            db.collection("users").document(uid).collection("walletDocuments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudWalletDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncWalletDocument(doc: WalletDocument): Result<Unit> {
        var path = "users/unauthenticated/walletDocuments"
        return try {
            val uid = requireUserId()
            val docId = sanitizeDocId(doc.id)
            val docRef = db.collection("users").document(uid).collection("walletDocuments").document(docId)
            path = docRef.path
            val snapshot = docRef.get().await()
            val safeTitle = doc.title.trim().ifBlank { "Document" }.take(120)
            val safeNotes = doc.notes.take(500).ifBlank { null }
            val offsetsCsv = doc.reminderOffsetsDays.distinct().sortedDescending().joinToString(",").ifBlank { "90,30,7,1" }.take(64)
            val historySummary = doc.renewalHistory.joinToString("||").take(500).ifBlank { null }

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "category" to doc.category.name,
                    "notes" to safeNotes,
                    "issueDateMillis" to doc.issueDateMillis,
                    "expiryDateMillis" to doc.expiryDateMillis.coerceAtLeast(0L),
                    "reminderOffsetsCsv" to offsetsCsv,
                    "renewed" to doc.isRenewed,
                    "lastRenewedAtMillis" to doc.lastRenewedAtMillis,
                    "renewalHistorySummary" to historySummary,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to safeTitle,
                    "category" to doc.category.name,
                    "notes" to safeNotes,
                    "issueDateMillis" to doc.issueDateMillis,
                    "expiryDateMillis" to doc.expiryDateMillis.coerceAtLeast(0L),
                    "reminderOffsetsCsv" to offsetsCsv,
                    "renewed" to doc.isRenewed,
                    "lastRenewedAtMillis" to doc.lastRenewedAtMillis,
                    "renewalHistorySummary" to historySummary,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteWalletDocument(docId: String): Result<Unit> {
        var path = "users/unauthenticated/walletDocuments/$docId"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(docId)
            val docRef = db.collection("users").document(uid).collection("walletDocuments").document(safeId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncSmartPreset(preset: SmartPreset): Result<Unit> {
        var path = "users/unauthenticated/smartPresets"
        return try {
            val uid = requireUserId()
            val presetId = sanitizeDocId(preset.id)
            val docRef = db.collection("users").document(uid).collection("smartPresets").document(presetId)
            path = docRef.path
            val snapshot = docRef.get().await()
            val safeName = preset.name.trim().ifBlank { "Custom Preset" }.take(80)
            val safeMaxBytes = preset.maxSizeBytes.coerceIn(1024L, 524_288_000L)
            val safeQuality = preset.qualityPercent.coerceIn(10, 100)

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to safeName,
                    "targetFormat" to preset.targetFormat.name,
                    "maxSizeBytes" to safeMaxBytes,
                    "minWidthPx" to preset.minWidthPx.coerceIn(0, 20000),
                    "maxWidthPx" to preset.maxWidthPx.coerceIn(0, 20000),
                    "minHeightPx" to preset.minHeightPx.coerceIn(0, 20000),
                    "maxHeightPx" to preset.maxHeightPx.coerceIn(0, 20000),
                    "aspectRatio" to preset.aspectRatioLabel.take(24).ifBlank { null },
                    "pdfPageSize" to preset.pdfPageSize.name.take(24),
                    "qualityPercent" to safeQuality,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to safeName,
                    "targetFormat" to preset.targetFormat.name,
                    "maxSizeBytes" to safeMaxBytes,
                    "minWidthPx" to preset.minWidthPx.coerceIn(0, 20000),
                    "maxWidthPx" to preset.maxWidthPx.coerceIn(0, 20000),
                    "minHeightPx" to preset.minHeightPx.coerceIn(0, 20000),
                    "maxHeightPx" to preset.maxHeightPx.coerceIn(0, 20000),
                    "aspectRatio" to preset.aspectRatioLabel.take(24).ifBlank { null },
                    "pdfPageSize" to preset.pdfPageSize.name.take(24),
                    "qualityPercent" to safeQuality,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(
        category: String,
        subject: String,
        message: String,
        appVersion: String
    ): Result<Unit> {
        var path = "users/unauthenticated/feedback"
        return try {
            val uid = requireUserId()
            val feedbackId = sanitizeDocId("fb_${System.currentTimeMillis()}")
            val docRef = db.collection("users").document(uid).collection("feedback").document(feedbackId)
            path = docRef.path
            val safeCategory = if (category in listOf("BUG", "FEATURE", "BILLING", "OTHER")) category else "OTHER"
            val payload = mapOf(
                "userId" to uid,
                "category" to safeCategory,
                "subject" to subject.trim().ifBlank { "Feedback" }.take(120),
                "message" to message.trim().take(1500),
                "appVersion" to appVersion.take(40).ifBlank { "1.0.0" },
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val userDoc = db.collection("users").document(uid)
            for (sub in listOf("walletDocuments", "smartPresets", "feedback")) {
                val docs = userDoc.collection(sub).get().await()
                for (doc in docs.documents) {
                    doc.reference.delete().await()
                }
            }
            userDoc.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
