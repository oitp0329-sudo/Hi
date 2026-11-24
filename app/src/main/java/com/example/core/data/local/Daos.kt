package com.example.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceFileDao {
    @Query("SELECT * FROM workspace_files WHERE isInTrash = 0 ORDER BY lastAccessedAtMillis DESC")
    fun observeActiveFiles(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE isInTrash = 1 ORDER BY lastAccessedAtMillis DESC")
    fun observeTrashFiles(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE isInTrash = 0 AND isFavourite = 1 ORDER BY lastAccessedAtMillis DESC")
    fun observeFavourites(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkspaceFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkspaceFileEntity)

    @Query("UPDATE workspace_files SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: String, fav: Boolean)

    @Query("UPDATE workspace_files SET displayName = :newName, localPath = :newPath WHERE id = :id")
    suspend fun rename(id: String, newName: String, newPath: String)

    @Query("UPDATE workspace_files SET folderName = :folderName WHERE id = :id")
    suspend fun moveToFolder(id: String, folderName: String)

    @Query("UPDATE workspace_files SET isInTrash = :inTrash, lastAccessedAtMillis = :ts WHERE id = :id")
    suspend fun setTrashed(id: String, inTrash: Boolean, ts: Long)

    @Query("DELETE FROM workspace_files WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM workspace_files WHERE isInTrash = 1")
    suspend fun emptyTrash()
}

@Dao
interface WalletDocumentDao {
    @Query("SELECT * FROM wallet_documents ORDER BY expiryDateMillis ASC")
    fun observeAll(): Flow<List<WalletDocumentEntity>>

    @Query("SELECT * FROM wallet_documents")
    suspend fun getAllOnce(): List<WalletDocumentEntity>

    @Query("SELECT * FROM wallet_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WalletDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WalletDocumentEntity)

    @Query("DELETE FROM wallet_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM wallet_documents")
    suspend fun deleteAll()
}

@Dao
interface SmartPresetDao {
    @Query("SELECT * FROM smart_presets ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<SmartPresetEntity>>

    @Query("SELECT COUNT(*) FROM smart_presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmartPresetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<SmartPresetEntity>)

    @Query("DELETE FROM smart_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: String)
}

@Dao
interface ProcessingTaskDao {
    @Query("SELECT * FROM processing_tasks ORDER BY createdAtMillis DESC LIMIT 50")
    fun observeRecentTasks(): Flow<List<ProcessingTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProcessingTaskEntity)

    @Query("DELETE FROM processing_tasks")
    suspend fun clearAll()
}
