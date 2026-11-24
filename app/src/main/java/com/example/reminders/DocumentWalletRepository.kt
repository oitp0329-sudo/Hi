package com.example.reminders

import com.example.core.data.local.WalletDocumentDao
import com.example.core.data.local.WalletDocumentEntity
import com.example.core.data.remote.CloudSyncRepository
import com.example.core.model.WalletDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentWalletRepository(
    private val dao: WalletDocumentDao,
    private val cloudSyncRepository: CloudSyncRepository
) {
    val documents: Flow<List<WalletDocument>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun saveDocument(doc: WalletDocument, syncToCloud: Boolean): Result<Unit> {
        val updated = doc.copy(updatedAtMillis = System.currentTimeMillis())
        dao.upsert(WalletDocumentEntity.fromDomain(updated))
        return if (syncToCloud) {
            cloudSyncRepository.syncWalletDocument(updated)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun markAsRenewed(
        doc: WalletDocument,
        newExpiryDateMillis: Long,
        syncToCloud: Boolean
    ): Result<Unit> {
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val entry = "Renewed on $dateStr"
        val updated = doc.copy(
            isRenewed = true,
            expiryDateMillis = newExpiryDateMillis,
            lastRenewedAtMillis = System.currentTimeMillis(),
            renewalHistory = (doc.renewalHistory + entry).takeLast(12),
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsert(WalletDocumentEntity.fromDomain(updated))
        return if (syncToCloud) {
            cloudSyncRepository.syncWalletDocument(updated)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteDocument(docId: String, syncToCloud: Boolean): Result<Unit> {
        dao.deleteById(docId)
        return if (syncToCloud) {
            cloudSyncRepository.deleteWalletDocument(docId)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun pullCloudDocumentsIntoLocal(): Result<Int> {
        return try {
            var count = 0
            // One-shot merge uses cloud documents if newer or missing locally
            val localById = dao.getAllOnce().associateBy { it.id }
            cloudSyncRepository.observeCloudWalletDocuments().collect { cloudDocs ->
                cloudDocs.forEach { cloud ->
                    val local = localById[cloud.id]
                    if (local == null) {
                        dao.upsert(WalletDocumentEntity.fromDomain(cloud))
                        count++
                    }
                }
                throw StopCollectSignal()
            }
            Result.success(count)
        } catch (_: StopCollectSignal) {
            Result.success(0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class StopCollectSignal : Exception()
}
