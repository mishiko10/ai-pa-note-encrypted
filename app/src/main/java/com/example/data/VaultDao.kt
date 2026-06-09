package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getActiveItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItemById(id: Int): VaultItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItem)

    @Update
    suspend fun updateItem(item: VaultItem)

    @Delete
    suspend fun deleteItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE isDeleted = 1")
    suspend fun clearTrash()
}
